#Requires -Version 7.0

[CmdletBinding()]
param(
    [int]$RequestCount = 10,
    [int]$RecoveryTimeoutSeconds = 180,
    [bool]$LeaveEvidenceContainersStopped = $true
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repositoryRoot 'backend\.env'
$workerJar = Join-Path $repositoryRoot 'backend\build\libs\backend-0.0.1-SNAPSHOT.jar'
$debeziumConfig = Join-Path $repositoryRoot 'infra\debezium\application-reliability-exp3.properties'
$runStartedAt = Get-Date
$token = $runStartedAt.ToString('yyyyMMddHHmmss')
$runId = "cdc-offset-mismatch-replay-$($runStartedAt.ToString('yyyyMMdd-HHmmss'))"
$runDirectory = Join-Path $repositoryRoot "docs\reliability-tests\runs\$runId"
$detectDataDirectory = Join-Path $runDirectory 'detect-data'
$replayDataDirectory = Join-Path $runDirectory 'replay-data'
$checkpointDirectory = Join-Path $runDirectory 'offset-checkpoints'
$transcriptPath = Join-Path $runDirectory 'commands.log'
$transcriptStarted = $false
$worker = $null
$experimentSucceeded = $false

$postgresContainer = "planmate-exp3-postgres-$token"
$detectContainer = "planmate-exp3-detect-$token"
$replayContainer = "planmate-exp3-replay-$token"
$postgresVolume = "planmate-exp3-postgres-data-$token"
$postgresAlias = "planmate-exp3-postgres-$token"
$detectSlot = "planmate_exp3_detect_$token"
$detectPublication = "planmate_exp3_detect_pub_$token"
$replaySlot = "planmate_exp3_replay_$token"
$replayPublication = "planmate_exp3_replay_pub_$token"
$experimentExchange = "planmate.reliability.exp3.$token"
$experimentQueue = "$experimentExchange.events"
$auditQueue = "$experimentExchange.replay.audit"
$routingKey = 'exp3.outbox'
$mainExchange = 'planmate.itinerary'
$mainRoutingKey = 'itinerary.generation.requested'
$mainQueue = 'planmate.itinerary.generation.requested'
$dlqName = 'planmate.itinerary.generation.requested.dlq'

New-Item -ItemType Directory -Path $runDirectory, $detectDataDirectory, $replayDataDirectory, $checkpointDirectory -Force | Out-Null

function Write-Step([string]$Message) {
    Write-Host ('[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss.fff'), $Message)
}

function Read-DotEnv([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^[A-Za-z_][A-Za-z0-9_]*=') {
            $pair = $line -split '=', 2
            $values[$pair[0]] = $pair[1]
        }
    }
    return $values
}

function Invoke-Docker([string[]]$Arguments, [bool]$AllowFailure = $false) {
    $output = & docker @Arguments 2>&1
    if (-not $AllowFailure -and $LASTEXITCODE -ne 0) {
        throw "Docker command failed ($LASTEXITCODE): docker $($Arguments -join ' ')`n$($output | Out-String)"
    }
    return $output
}

function Test-ContainerExists([string]$Name) {
    $names = Invoke-Docker -Arguments @('ps', '-a', '--format', '{{.Names}}')
    return $names -contains $Name
}

function Get-ContainerState([string]$Name) {
    if (-not (Test-ContainerExists $Name)) { return 'missing' }
    return ((Invoke-Docker -Arguments @('inspect', '--format', '{{.State.Status}}', $Name)) | Out-String).Trim()
}

function Stop-ContainerIfRunning([string]$Name) {
    if ((Get-ContainerState $Name) -eq 'running') {
        Invoke-Docker -Arguments @('stop', '--time', '10', $Name) | Out-Null
    }
}

function Wait-PostgresReady([int]$TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        & docker exec $postgresContainer pg_isready -U planmate_exp3 -d planmate_exp3 *> $null
        if ($LASTEXITCODE -eq 0) { return Get-Date }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw 'Experiment PostgreSQL did not become ready'
}

function Invoke-ExperimentSql([string]$Sql, [bool]$TuplesOnly = $false) {
    $arguments = @('exec', $postgresContainer, 'psql', '-U', 'planmate_exp3', '-d', 'planmate_exp3', '-v', 'ON_ERROR_STOP=1')
    if ($TuplesOnly) { $arguments += @('-At') }
    $arguments += @('-c', $Sql)
    $output = Invoke-Docker -Arguments $arguments
    return (($output | Out-String).Trim())
}

function Invoke-ProductionScalar([string]$Sql) {
    $output = Invoke-Docker -Arguments @(
        'exec', 'planmate-postgres', 'psql', '-U', $script:settings['POSTGRES_USER'],
        '-d', $script:settings['POSTGRES_DB'], '-At', '-v', 'ON_ERROR_STOP=1', '-c', $Sql
    )
    return (($output | Out-String).Trim())
}

function Get-SlotSnapshot([string]$SlotName) {
    $sql = @"
SELECT row_to_json(slot_state)::text
FROM (
  SELECT slot_name,
         restart_lsn::text AS restart_lsn,
         confirmed_flush_lsn::text AS confirmed_flush_lsn,
         active
  FROM pg_replication_slots
  WHERE slot_name = '$SlotName'
) slot_state;
"@
    $json = Invoke-ExperimentSql -Sql $sql -TuplesOnly $true
    if ([string]::IsNullOrWhiteSpace($json)) {
        throw "Replication slot not found: $SlotName"
    }
    return ($json | ConvertFrom-Json)
}

function Get-DebeziumHealth([int]$Port) {
    try {
        return (Invoke-RestMethod -Uri "http://localhost:$Port/q/health" -TimeoutSec 3).status
    } catch {
        return 'DOWN'
    }
}

function Wait-DebeziumHealth([int]$Port, [string]$Expected, [int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $status = Get-DebeziumHealth -Port $Port
        if ($status -eq $Expected) { return Get-Date }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Wait-WorkerHealth([int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $health = Invoke-RestMethod -Uri 'http://localhost:8081/actuator/health' -TimeoutSec 3
            if ($health.status -eq 'UP') { return Get-Date }
        } catch {
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Get-PrometheusValue([string]$Query) {
    $encoded = [Uri]::EscapeDataString($Query)
    $response = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/query?query=$encoded" -TimeoutSec 10
    if ($response.status -ne 'success' -or $response.data.result.Count -eq 0) { return $null }
    return [double]$response.data.result[0].value[1]
}

function Wait-PrometheusValue([string]$Query, [double]$Expected, [int]$TimeoutSeconds = 45) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $value = Get-PrometheusValue -Query $Query
        if ($null -ne $value -and $value -eq $Expected) { return Get-Date }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $null
}

function New-RabbitHeaders {
    $credential = [Text.Encoding]::ASCII.GetBytes(
        $script:settings['RABBITMQ_USERNAME'] + ':' + $script:settings['RABBITMQ_PASSWORD']
    )
    return @{ Authorization = 'Basic ' + [Convert]::ToBase64String($credential) }
}

function Ensure-RabbitExchange([string]$Name, [hashtable]$Headers) {
    $encoded = [Uri]::EscapeDataString($Name)
    $body = @{ type = 'direct'; durable = $true; auto_delete = $false; internal = $false; arguments = @{} } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Put -Uri "http://localhost:15672/api/exchanges/%2F/$encoded" -Headers $Headers -ContentType 'application/json' -Body $body | Out-Null
}

function Ensure-RabbitQueue([string]$Name, [hashtable]$Headers) {
    $encoded = [Uri]::EscapeDataString($Name)
    $body = @{ durable = $true; auto_delete = $false; arguments = @{} } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Put -Uri "http://localhost:15672/api/queues/%2F/$encoded" -Headers $Headers -ContentType 'application/json' -Body $body | Out-Null
}

function Ensure-RabbitBinding([string]$Exchange, [string]$Queue, [string]$Key, [hashtable]$Headers) {
    $encodedExchange = [Uri]::EscapeDataString($Exchange)
    $encodedQueue = [Uri]::EscapeDataString($Queue)
    $body = @{ routing_key = $Key; arguments = @{} } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Post -Uri "http://localhost:15672/api/bindings/%2F/e/$encodedExchange/q/$encodedQueue" -Headers $Headers -ContentType 'application/json' -Body $body | Out-Null
}

function Get-RabbitQueueSnapshot([string]$Name, [hashtable]$Headers) {
    $encoded = [Uri]::EscapeDataString($Name)
    $queue = Invoke-RestMethod -Uri "http://localhost:15672/api/queues/%2F/$encoded" -Headers $Headers -TimeoutSec 10
    return [ordered]@{
        name = $Name
        capturedAt = (Get-Date).ToUniversalTime().ToString('o')
        ready = [long]($queue.messages_ready ?? 0)
        unacked = [long]($queue.messages_unacknowledged ?? 0)
        consumers = [long]($queue.consumers ?? 0)
        publish = [long]($queue.message_stats.publish ?? 0)
        deliver = [long]($queue.message_stats.deliver_get ?? 0)
        ack = [long]($queue.message_stats.ack ?? 0)
        redeliver = [long]($queue.message_stats.redeliver ?? 0)
    }
}

function Wait-QueueReady([string]$Name, [long]$Expected, [hashtable]$Headers, [int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $snapshot = Get-RabbitQueueSnapshot -Name $Name -Headers $Headers
        if ($snapshot.ready -eq $Expected) { return $snapshot }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Queue $Name did not reach ready=$Expected; last=$($snapshot.ready)"
}

function Get-RabbitMessages([string]$Name, [int]$Count, [hashtable]$Headers) {
    $encoded = [Uri]::EscapeDataString($Name)
    $body = @{ count = $Count; ackmode = 'ack_requeue_false'; encoding = 'auto'; truncate = 50000 } | ConvertTo-Json -Compress
    $messages = Invoke-RestMethod -Method Post -Uri "http://localhost:15672/api/queues/%2F/$encoded/get" -Headers $Headers -ContentType 'application/json' -Body $body -TimeoutSec 30
    return @($messages)
}

function Add-OutboxEvent([string]$AggregateId, [hashtable]$Payload) {
    $eventId = [guid]::NewGuid().ToString()
    $payloadJson = ($Payload | ConvertTo-Json -Compress).Replace("'", "''")
    $sql = @"
INSERT INTO outbox_events(id, aggregate_type, aggregate_id, event_type, payload, created_at)
VALUES ('$eventId'::uuid, 'ITINERARY_GENERATION', '$AggregateId',
        'ITINERARY_GENERATION_REQUESTED', '$payloadJson'::jsonb, now());
"@
    Invoke-ExperimentSql -Sql $sql | Out-Null
    return $eventId
}

function Get-GenerationIdsFromMessages([object[]]$Messages) {
    $ids = foreach ($message in $Messages) {
        $payload = $message.payload | ConvertFrom-Json
        [long]$payload.generationId
    }
    return [long[]]$ids
}

function Start-DebeziumContainer(
    [string]$Name,
    [string]$DataDirectory,
    [int]$HttpPort,
    [int]$MetricsPort,
    [string]$Slot,
    [string]$Publication,
    [string]$SnapshotMode,
    [string]$Exchange,
    [string]$Route,
    [string]$TopicPrefix
) {
    $configMount = $debeziumConfig.Replace('\', '/')
    $dataMount = $DataDirectory.Replace('\', '/')
    $arguments = @(
        'run', '-d', '--name', $Name,
        '--network', 'planmate-local',
        '-p', "${HttpPort}:8080",
        '-p', "${MetricsPort}:9404",
        '--mount', "type=bind,source=$configMount,target=/debezium/config/application.properties,readonly",
        '--mount', "type=bind,source=$dataMount,target=/debezium/data",
        '-e', "POSTGRES_HOST=$postgresAlias",
        '-e', 'POSTGRES_PORT=5432',
        '-e', 'POSTGRES_DB=planmate_exp3',
        '-e', 'POSTGRES_USER=planmate_exp3',
        '-e', 'POSTGRES_PASSWORD=planmate_exp3',
        '-e', 'RABBITMQ_HOST=planmate-rabbitmq',
        '-e', 'RABBITMQ_PORT=5672',
        '-e', "RABBITMQ_USERNAME=$($script:settings['RABBITMQ_USERNAME'])",
        '-e', "RABBITMQ_PASSWORD=$($script:settings['RABBITMQ_PASSWORD'])",
        '-e', "DEBEZIUM_SLOT_NAME=$Slot",
        '-e', "DEBEZIUM_PUBLICATION_NAME=$Publication",
        '-e', "DEBEZIUM_SNAPSHOT_MODE=$SnapshotMode",
        '-e', 'DEBEZIUM_OFFSET_MISMATCH_STRATEGY=trust_offset',
        '-e', "DEBEZIUM_EXCHANGE=$Exchange",
        '-e', "DEBEZIUM_ROUTING_KEY=$Route",
        '-e', "DEBEZIUM_TOPIC_PREFIX=$TopicPrefix",
        '-e', 'JMX_EXPORTER_PORT=9404',
        'quay.io/debezium/server:3.5'
    )
    Invoke-Docker -Arguments $arguments | Out-Null
}

function Start-WorkerProcess {
    foreach ($pair in $script:settings.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($pair.Key, $pair.Value, 'Process')
    }
    $env:DB_URL = 'jdbc:postgresql://localhost:15432/planmate'
    $env:SPRING_DOCKER_COMPOSE_ENABLED = 'false'
    $env:SERVER_PORT = '8081'
    $env:APP_ITINERARY_MANUAL_HANDOFF_ENABLED = 'false'
    $env:APP_ITINERARY_GENERATION_WORKER_ENABLED = 'true'
    $env:APP_ITINERARY_GENERATION_WORKER_STALE_RECOVERY_ENABLED = 'false'
    $env:APP_ITINERARY_GENERATION_WORKER_RELIABILITY_AFTER_COMMIT_BEFORE_ACK_DELAY = '0s'
    $env:APP_ITINERARY_CANDIDATES_PROVIDER = 'deterministic'
    $env:SPRING_RABBITMQ_LISTENER_SIMPLE_PREFETCH = '1'

    $stdout = Join-Path $runDirectory 'replay-worker.out.log'
    $stderr = Join-Path $runDirectory 'replay-worker.err.log'
    return Start-Process -FilePath (Get-Command java).Source `
        -ArgumentList @('-jar', $workerJar) `
        -WorkingDirectory $repositoryRoot `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru
}

function Stop-OwnedProcess($Process) {
    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit(15000) | Out-Null
    }
}

function Get-WorkerMetric([string]$Metric, [string]$TagName, [string]$TagValue) {
    try {
        $text = Invoke-RestMethod -Uri 'http://localhost:8081/actuator/prometheus' -TimeoutSec 5
        $escapedValue = [regex]::Escape($TagValue)
        $pattern = "(?m)^$([regex]::Escape($Metric))\{[^}]*$TagName=`"$escapedValue`"[^}]*\}\s+([0-9.]+)$"
        $match = [regex]::Match($text, $pattern)
        if ($match.Success) { return [double]$match.Groups[1].Value }
    } catch {
    }
    return 0
}

function Get-ProductionSnapshot {
    $sql = @"
SELECT json_build_object(
  'readyCount', (SELECT count(*) FROM itinerary_generations WHERE id BETWEEN 1166 AND 1175 AND status = 'READY_FOR_PLANNING'),
  'failedCount', (SELECT count(*) FROM itinerary_generations WHERE id BETWEEN 1166 AND 1175 AND status = 'FAILED'),
  'candidateRows', (SELECT count(*) FROM place_candidates WHERE generation_id BETWEEN 1166 AND 1175),
  'distinctGenerationPlaceRows', (SELECT count(DISTINCT (generation_id, place_id)) FROM place_candidates WHERE generation_id BETWEEN 1166 AND 1175),
  'distinctGenerationRankRows', (SELECT count(DISTINCT (generation_id, rank)) FROM place_candidates WHERE generation_id BETWEEN 1166 AND 1175)
)::text;
"@
    return (Invoke-ProductionScalar -Sql $sql | ConvertFrom-Json)
}

function Get-Delta($After, $Before, [string]$Property) {
    return [long]$After[$Property] - [long]$Before[$Property]
}

function ConvertTo-Seconds([datetime]$End, [datetime]$Start) {
    return [Math]::Round(($End - $Start).TotalSeconds, 3)
}

$settings = Read-DotEnv -Path $envFile
foreach ($required in @('RABBITMQ_USERNAME', 'RABBITMQ_PASSWORD', 'POSTGRES_USER', 'POSTGRES_DB')) {
    if (-not $settings.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($settings[$required])) {
        throw "Required setting is missing: $required"
    }
}

try {
    Start-Transcript -LiteralPath $transcriptPath | Out-Null
    $transcriptStarted = $true
    Write-Step "Run directory: $runDirectory"
    Write-Step 'Checking safe isolated-environment prerequisites'

    if (-not (Test-Path -LiteralPath $workerJar)) { throw "Worker JAR not found: $workerJar" }
    if (-not (Test-Path -LiteralPath $debeziumConfig)) { throw "Debezium experiment config not found: $debeziumConfig" }
    foreach ($name in @($postgresContainer, $detectContainer, $replayContainer)) {
        if (Test-ContainerExists $name) { throw "Experiment container already exists: $name" }
    }
    foreach ($port in @(15433, 8084, 9705, 8085, 9706, 8081)) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            throw "Required experiment port is already in use: $port"
        }
    }
    $apiHealth = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 5
    if ($apiHealth.status -ne 'UP') { throw 'PlanMate API is not UP' }

    $rabbitHeaders = New-RabbitHeaders
    $mainPreflight = Get-RabbitQueueSnapshot -Name $mainQueue -Headers $rabbitHeaders
    if ($mainPreflight.ready -ne 0 -or $mainPreflight.unacked -ne 0 -or $mainPreflight.consumers -ne 0) {
        throw "Main queue must be idle before experiment: ready=$($mainPreflight.ready), unacked=$($mainPreflight.unacked), consumers=$($mainPreflight.consumers)"
    }
    $productionPreflight = Get-ProductionSnapshot
    if ($productionPreflight.readyCount -ne $RequestCount -or $productionPreflight.candidateRows -ne ($RequestCount * 120)) {
        throw "Expected existing READY generations 1166-1175 with 120 candidates each"
    }

    Ensure-RabbitExchange -Name $experimentExchange -Headers $rabbitHeaders
    Ensure-RabbitQueue -Name $experimentQueue -Headers $rabbitHeaders
    Ensure-RabbitBinding -Exchange $experimentExchange -Queue $experimentQueue -Key $routingKey -Headers $rabbitHeaders
    Ensure-RabbitQueue -Name $auditQueue -Headers $rabbitHeaders
    Ensure-RabbitBinding -Exchange $mainExchange -Queue $auditQueue -Key $mainRoutingKey -Headers $rabbitHeaders

    $timeline = [System.Collections.Generic.List[object]]::new()
    $timeline.Add([pscustomobject]@{ event = 'T0_PREFLIGHT_READY'; at = (Get-Date).ToUniversalTime().ToString('o') })

    Write-Step "Starting isolated PostgreSQL container $postgresContainer"
    Invoke-Docker -Arguments @(
        'run', '-d', '--name', $postgresContainer,
        '--network', 'planmate-local', '--network-alias', $postgresAlias,
        '-e', 'POSTGRES_DB=planmate_exp3',
        '-e', 'POSTGRES_USER=planmate_exp3',
        '-e', 'POSTGRES_PASSWORD=planmate_exp3',
        '-p', '15433:5432',
        '-v', "${postgresVolume}:/var/lib/postgresql/data",
        'postgres:17', 'postgres',
        '-c', 'wal_level=logical',
        '-c', 'max_wal_senders=10',
        '-c', 'max_replication_slots=10'
    ) | Out-Null
    $postgresReadyAt = Wait-PostgresReady
    $timeline.Add([pscustomobject]@{ event = 'T1_ISOLATED_POSTGRES_UP'; at = $postgresReadyAt.ToUniversalTime().ToString('o') })

    Invoke-ExperimentSql -Sql @"
CREATE TABLE outbox_events (
  id uuid PRIMARY KEY,
  aggregate_type varchar(100) NOT NULL,
  aggregate_id varchar(100) NOT NULL,
  event_type varchar(100) NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
"@ | Out-Null

    Write-Step '3-A: starting detector connector with trust_offset and no_data snapshot'
    Start-DebeziumContainer -Name $detectContainer -DataDirectory $detectDataDirectory `
        -HttpPort 8084 -MetricsPort 9705 -Slot $detectSlot -Publication $detectPublication `
        -SnapshotMode 'no_data' -Exchange $experimentExchange -Route $routingKey -TopicPrefix 'planmate-exp3-detect'
    $detectInitialUpAt = Wait-DebeziumHealth -Port 8084 -Expected 'UP' -TimeoutSeconds 90
    if ($null -eq $detectInitialUpAt) { throw 'Detect connector did not become UP' }
    $timeline.Add([pscustomobject]@{ event = 'T2_DETECTOR_INITIAL_UP'; at = $detectInitialUpAt.ToUniversalTime().ToString('o') })

    $controlEvent1 = Add-OutboxEvent -AggregateId 'control-1' -Payload @{ generationId = 900001; tripId = 900001; userId = 1 }
    Wait-QueueReady -Name $experimentQueue -Expected 1 -Headers $rabbitHeaders | Out-Null
    Start-Sleep -Seconds 2
    Stop-ContainerIfRunning -Name $detectContainer
    $checkpoint1 = Join-Path $checkpointDirectory 'offsets-checkpoint-1.dat'
    $offsetFile = Join-Path $detectDataDirectory 'offsets.dat'
    if (-not (Test-Path -LiteralPath $offsetFile)) { throw 'Detect offset file was not created' }
    Copy-Item -LiteralPath $offsetFile -Destination $checkpoint1 -Force
    $slotCheckpoint1 = Get-SlotSnapshot -SlotName $detectSlot
    $timeline.Add([pscustomobject]@{ event = 'T3_CHECKPOINT_1_SAVED'; at = (Get-Date).ToUniversalTime().ToString('o') })

    Invoke-Docker -Arguments @('start', $detectContainer) | Out-Null
    if ($null -eq (Wait-DebeziumHealth -Port 8084 -Expected 'UP' -TimeoutSeconds 90)) {
        throw 'Detect connector did not restart for checkpoint 2'
    }
    $controlEvent2 = Add-OutboxEvent -AggregateId 'control-2' -Payload @{ generationId = 900002; tripId = 900002; userId = 1 }
    Wait-QueueReady -Name $experimentQueue -Expected 2 -Headers $rabbitHeaders | Out-Null
    Start-Sleep -Seconds 2
    Stop-ContainerIfRunning -Name $detectContainer
    $checkpoint2 = Join-Path $checkpointDirectory 'offsets-checkpoint-2.dat'
    Copy-Item -LiteralPath $offsetFile -Destination $checkpoint2 -Force
    $slotCheckpoint2 = Get-SlotSnapshot -SlotName $detectSlot
    $timeline.Add([pscustomobject]@{ event = 'T4_CHECKPOINT_2_SAVED_SLOT_AHEAD'; at = (Get-Date).ToUniversalTime().ToString('o') })

    Write-Step 'Injecting stale offset checkpoint while replication slot remains ahead'
    Copy-Item -LiteralPath $checkpoint1 -Destination $offsetFile -Force
    $mismatchStartAt = Get-Date
    Invoke-Docker -Arguments @('start', $detectContainer) | Out-Null
    # Match the connector's actual fail-safe error, not the informational line that
    # merely explains how the configured mismatch strategy behaves.
    $mismatchPattern = '(?i)(Last recorded offset is no longer available|connector is trying to read change stream starting at .*no longer available on the server)'
    $mismatchDeadline = (Get-Date).AddSeconds(60)
    $mismatchDetected = $false
    do {
        $detectLogs = (Invoke-Docker -Arguments @('logs', $detectContainer) -AllowFailure $true) | Out-String
        if ($detectLogs -match $mismatchPattern) {
            $mismatchDetected = $true
            break
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $mismatchDeadline)
    $mismatchDetectedAt = Get-Date
    $mismatchHealth = Get-DebeziumHealth -Port 8084
    $detectLogs | Set-Content -LiteralPath (Join-Path $runDirectory 'detect-mismatch.log') -Encoding utf8
    if (-not $mismatchDetected) { throw 'trust_offset mismatch error was not observed in Debezium logs' }
    if ($mismatchHealth -ne 'DOWN') { throw "Expected detector Health DOWN during mismatch, got $mismatchHealth" }
    $timeline.Add([pscustomobject]@{ event = 'T5_OFFSET_MISMATCH_DETECTED_HEALTH_DOWN'; at = $mismatchDetectedAt.ToUniversalTime().ToString('o') })
    Wait-PrometheusValue -Query 'debezium_metrics_Connected{job="debezium-exp3-detect"}' -Expected 0 -TimeoutSeconds 45 | Out-Null

    Write-Step 'Recovering detector with latest consistent offset checkpoint'
    Stop-ContainerIfRunning -Name $detectContainer
    Copy-Item -LiteralPath $checkpoint2 -Destination $offsetFile -Force
    $recoveryStartAt = Get-Date
    Invoke-Docker -Arguments @('start', $detectContainer) | Out-Null
    $detectRecoveredAt = Wait-DebeziumHealth -Port 8084 -Expected 'UP' -TimeoutSeconds 90
    if ($null -eq $detectRecoveredAt) { throw 'Detector did not recover with checkpoint 2' }
    $detectRecoverySeconds = ConvertTo-Seconds -End $detectRecoveredAt -Start $recoveryStartAt
    $timeline.Add([pscustomobject]@{ event = 'T6_LATEST_OFFSET_RESTORED_HEALTH_UP'; at = $detectRecoveredAt.ToUniversalTime().ToString('o') })
    Wait-PrometheusValue -Query 'debezium_metrics_Connected{job="debezium-exp3-detect"}' -Expected 1 -TimeoutSeconds 45 | Out-Null

    $controlMessages = Get-RabbitMessages -Name $experimentQueue -Count 20 -Headers $rabbitHeaders
    $controlMessages | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDirectory 'control-messages.json') -Encoding utf8
    if ($controlMessages.Count -ne 2) { throw "Expected 2 control messages, got $($controlMessages.Count)" }

    Write-Step '3-B: preparing 10 processed Generation events in isolated Outbox'
    Invoke-ExperimentSql -Sql 'TRUNCATE TABLE outbox_events;' | Out-Null
    $generationIds = 1166..1175
    $tripIds = 1245..1254
    $sourceEventIds = [System.Collections.Generic.List[string]]::new()
    for ($index = 0; $index -lt $RequestCount; $index++) {
        $sourceEventIds.Add((Add-OutboxEvent -AggregateId "$($generationIds[$index])" -Payload @{
            generationId = $generationIds[$index]
            tripId = $tripIds[$index]
            userId = 1
        }))
    }
    $sourceEventsCreatedAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T7_TEN_SOURCE_EVENTS_CREATED'; at = $sourceEventsCreatedAt.ToUniversalTime().ToString('o') })
    $sourceQueueAtTen = Wait-QueueReady -Name $experimentQueue -Expected $RequestCount -Headers $rabbitHeaders -TimeoutSeconds 90
    $originalMessages = Get-RabbitMessages -Name $experimentQueue -Count 20 -Headers $rabbitHeaders
    $originalMessages | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDirectory 'original-delivery-messages.json') -Encoding utf8
    $originalGenerationIds = Get-GenerationIdsFromMessages -Messages $originalMessages
    if ($originalMessages.Count -ne $RequestCount) { throw "Expected $RequestCount original messages, got $($originalMessages.Count)" }
    Stop-ContainerIfRunning -Name $detectContainer

    $productionBefore = Get-ProductionSnapshot
    $mainBefore = Get-RabbitQueueSnapshot -Name $mainQueue -Headers $rabbitHeaders
    $dlqBefore = Get-RabbitQueueSnapshot -Name $dlqName -Headers $rabbitHeaders
    $auditBefore = Get-RabbitQueueSnapshot -Name $auditQueue -Headers $rabbitHeaders
    if ($auditBefore.ready -ne 0) { throw 'Replay audit queue is not empty before replay' }

    Write-Step 'Starting isolated Worker for replay idempotency verification'
    $worker = Start-WorkerProcess
    $workerUpAt = Wait-WorkerHealth -TimeoutSeconds 90
    if ($null -eq $workerUpAt) { throw 'Replay verification Worker did not become UP' }
    $timeline.Add([pscustomobject]@{ event = 'T8_REPLAY_WORKER_UP'; at = $workerUpAt.ToUniversalTime().ToString('o') })

    Write-Step 'Starting new-slot initial snapshot to replay the same 10 past events'
    $replayStartAt = Get-Date
    Start-DebeziumContainer -Name $replayContainer -DataDirectory $replayDataDirectory `
        -HttpPort 8085 -MetricsPort 9706 -Slot $replaySlot -Publication $replayPublication `
        -SnapshotMode 'initial' -Exchange $mainExchange -Route $mainRoutingKey -TopicPrefix 'planmate-exp3-replay'
    $replayUpAt = Wait-DebeziumHealth -Port 8085 -Expected 'UP' -TimeoutSeconds 90
    if ($null -eq $replayUpAt) { throw 'Replay connector did not become UP' }
    $timeline.Add([pscustomobject]@{ event = 'T9_REPLAY_CONNECTOR_UP'; at = $replayUpAt.ToUniversalTime().ToString('o') })

    $replayDeadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    do {
        $auditDuring = Get-RabbitQueueSnapshot -Name $auditQueue -Headers $rabbitHeaders
        $mainDuring = Get-RabbitQueueSnapshot -Name $mainQueue -Headers $rabbitHeaders
        $skippedMetric = Get-WorkerMetric -Metric 'planmate_itinerary_generation_worker_processed_total' -TagName 'result' -TagValue 'skipped'
        if ($auditDuring.ready -eq $RequestCount -and $mainDuring.ready -eq 0 -and $mainDuring.unacked -eq 0 -and $skippedMetric -eq $RequestCount) {
            break
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $replayDeadline)
    if ($auditDuring.ready -ne $RequestCount -or $skippedMetric -ne $RequestCount) {
        throw "Replay did not settle: audit=$($auditDuring.ready), skipped=$skippedMetric, main=$($mainDuring.ready)/$($mainDuring.unacked)"
    }
    $replayCompletedAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T10_REPLAY_TEN_SKIPPED_QUEUE_SETTLED'; at = $replayCompletedAt.ToUniversalTime().ToString('o') })
    Start-Sleep -Seconds 2

    $replayMessages = Get-RabbitMessages -Name $auditQueue -Count 20 -Headers $rabbitHeaders
    $replayMessages | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDirectory 'replayed-snapshot-messages.json') -Encoding utf8
    $replayedGenerationIds = Get-GenerationIdsFromMessages -Messages $replayMessages
    $productionAfter = Get-ProductionSnapshot
    $mainAfter = Get-RabbitQueueSnapshot -Name $mainQueue -Headers $rabbitHeaders
    $dlqAfter = Get-RabbitQueueSnapshot -Name $dlqName -Headers $rabbitHeaders
    $auditAfter = Get-RabbitQueueSnapshot -Name $auditQueue -Headers $rabbitHeaders
    $detectSlotFinal = Get-SlotSnapshot -SlotName $detectSlot
    $replaySlotFinal = Get-SlotSnapshot -SlotName $replaySlot

    $sameReplaySet = (@($originalGenerationIds | Sort-Object) -join ',') -eq (@($replayedGenerationIds | Sort-Object) -join ',')
    $candidateDuplicateRows = [long]$productionAfter.candidateRows - [long]$productionAfter.distinctGenerationPlaceRows
    $rankDuplicateRows = [long]$productionAfter.candidateRows - [long]$productionAfter.distinctGenerationRankRows
    $mainPublishDelta = Get-Delta -After $mainAfter -Before $mainBefore -Property 'publish'
    $mainDeliverDelta = Get-Delta -After $mainAfter -Before $mainBefore -Property 'deliver'
    $mainAckDelta = Get-Delta -After $mainAfter -Before $mainBefore -Property 'ack'
    $mainRedeliverDelta = Get-Delta -After $mainAfter -Before $mainBefore -Property 'redeliver'
    $dlqDelta = [long]$dlqAfter.ready - [long]$dlqBefore.ready
    $replaySeconds = ConvertTo-Seconds -End $replayCompletedAt -Start $replayStartAt
    $mismatchDetectionSeconds = ConvertTo-Seconds -End $mismatchDetectedAt -Start $mismatchStartAt

    $checks = [ordered]@{
        slotAdvancedBetweenCheckpoints = $slotCheckpoint1.confirmed_flush_lsn -ne $slotCheckpoint2.confirmed_flush_lsn
        staleOffsetExplicitlyDetected = $mismatchDetected
        mismatchHealthWasDown = $mismatchHealth -eq 'DOWN'
        latestOffsetRecovered = $detectRecoverySeconds -gt 0
        originalTenDelivered = $originalMessages.Count -eq $RequestCount
        replayTenDelivered = $replayMessages.Count -eq $RequestCount
        replayedSameGenerationSet = $sameReplaySet
        workerSkippedTen = $skippedMetric -eq $RequestCount
        allRemainReady = [long]$productionAfter.readyCount -eq $RequestCount
        noStateRegression = [long]$productionAfter.failedCount -eq 0
        candidateRowsUnchanged = [long]$productionAfter.candidateRows -eq [long]$productionBefore.candidateRows
        noDuplicateCandidates = $candidateDuplicateRows -eq 0 -and $rankDuplicateRows -eq 0
        mainPublishedTen = $mainPublishDelta -eq $RequestCount
        mainDeliveredTen = $mainDeliverDelta -eq $RequestCount
        mainAckedTen = $mainAckDelta -eq $RequestCount
        noBrokerRedelivery = $mainRedeliverDelta -eq 0
        noDlqIncrease = $dlqDelta -eq 0
        mainQueueDrained = $mainAfter.ready -eq 0 -and $mainAfter.unacked -eq 0
    }
    $failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object Key)
    $verdict = if ($failedChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }

    $result = [ordered]@{
        experimentId = $runId
        experimentName = 'CDC Offset 불일치 탐지와 과거 이벤트 재전달'
        startedAt = $runStartedAt.ToUniversalTime().ToString('o')
        finishedAt = (Get-Date).ToUniversalTime().ToString('o')
        commitSha = ((git -C $repositoryRoot rev-parse HEAD) | Out-String).Trim()
        environmentIsolation = 'Disposable PostgreSQL container, unique slots/publications/offset directories'
        offsetMismatchStrategy = 'trust_offset'
        snapshotModeDetection = 'no_data'
        snapshotModeReplay = 'initial'
        detectSlot = $detectSlot
        replaySlot = $replaySlot
        slotCheckpoint1 = $slotCheckpoint1
        slotCheckpoint2 = $slotCheckpoint2
        staleOffsetHealth = $mismatchHealth
        mismatchDetectionSeconds = $mismatchDetectionSeconds
        detectorRecoverySeconds = $detectRecoverySeconds
        sourceEvents = $RequestCount
        originalMessages = $originalMessages.Count
        replayedMessages = $replayMessages.Count
        logicalDuplicateMessages = $replayMessages.Count
        sameGenerationSet = $sameReplaySet
        workerSkipped = [long]$skippedMetric
        readyForPlanningBefore = [long]$productionBefore.readyCount
        readyForPlanningAfter = [long]$productionAfter.readyCount
        candidateRowsBefore = [long]$productionBefore.candidateRows
        candidateRowsAfter = [long]$productionAfter.candidateRows
        duplicateCandidateRows = $candidateDuplicateRows
        duplicateRankRows = $rankDuplicateRows
        mainPublishedDelta = $mainPublishDelta
        mainDeliveredDelta = $mainDeliverDelta
        mainAckedDelta = $mainAckDelta
        mainRedeliveredDelta = $mainRedeliverDelta
        dlqBefore = [long]$dlqBefore.ready
        dlqAfter = [long]$dlqAfter.ready
        dlqDelta = $dlqDelta
        lostEvents = [Math]::Max(0, $RequestCount - $replayMessages.Count)
        replayCompletionSeconds = $replaySeconds
        prometheusScrapeUncertaintySeconds = 15
        resources = [ordered]@{
            postgresContainer = $postgresContainer
            detectContainer = $detectContainer
            replayContainer = $replayContainer
            postgresVolume = $postgresVolume
            experimentExchange = $experimentExchange
            experimentQueue = $experimentQueue
            auditQueue = $auditQueue
        }
        checks = $checks
        failedChecks = $failedChecks
        verdict = $verdict
    }
    $result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDirectory 'result.json') -Encoding utf8
    $timeline | Export-Csv -LiteralPath (Join-Path $runDirectory 'timeline.csv') -NoTypeInformation -Encoding utf8
    [ordered]@{
        mainPreflight = $mainPreflight
        sourceQueueAtTen = $sourceQueueAtTen
        mainBefore = $mainBefore
        mainAfter = $mainAfter
        dlqBefore = $dlqBefore
        dlqAfter = $dlqAfter
        auditBefore = $auditBefore
        auditDuring = $auditDuring
        auditAfter = $auditAfter
    } | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $runDirectory 'rabbitmq-snapshots.json') -Encoding utf8
    [ordered]@{
        checkpoint1 = $slotCheckpoint1
        checkpoint2 = $slotCheckpoint2
        detectFinal = $detectSlotFinal
        replayFinal = $replaySlotFinal
    } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'replication-slot-snapshots.json') -Encoding utf8
    [ordered]@{
        sourceEventIds = $sourceEventIds
        originalGenerationIds = $originalGenerationIds
        replayedGenerationIds = $replayedGenerationIds
    } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'event-comparison.json') -Encoding utf8

    $readme = @"
# CDC Offset 불일치 탐지와 과거 이벤트 재전달 결과

- 실행 ID: $runId
- 3-A Offset 불일치 전략: trust_offset
- 3-B 재전달 대상: $RequestCount건
- 판정: **$verdict**

| 항목 | 실제값 |
| --- | ---: |
| 오래된 Offset 탐지 | $mismatchDetected |
| 불일치 시 Health | $mismatchHealth |
| 불일치 탐지 시간 | $mismatchDetectionSeconds 초 |
| 최신 Offset 복구 시간 | $detectRecoverySeconds 초 |
| 원본 전달 / 과거 이벤트 재전달 | $($originalMessages.Count) / $($replayMessages.Count) |
| Worker 멱등 SKIP | $skippedMetric 건 |
| READY 유지 | $($productionAfter.readyCount)/$RequestCount |
| Candidate 중복 | $candidateDuplicateRows 건 |
| 이벤트 유실 / DLQ 증가 | $([Math]::Max(0, $RequestCount - $replayMessages.Count)) / $dlqDelta 건 |
| 재전달 처리 완료 | $replaySeconds 초 |

실패한 검사: $($failedChecks -join ', ')
"@
    $readme | Set-Content -LiteralPath (Join-Path $runDirectory 'README.md') -Encoding utf8

    $experimentSucceeded = $verdict -eq 'PASS'
    Write-Step "Experiment verdict: $verdict"
    $result | ConvertTo-Json -Depth 20 | Write-Host
    if (-not $experimentSucceeded) { throw "Experiment failed checks: $($failedChecks -join ', ')" }
}
finally {
    try {
        if (Test-ContainerExists $detectContainer) {
            (Invoke-Docker -Arguments @('logs', $detectContainer) -AllowFailure $true) | Set-Content -LiteralPath (Join-Path $runDirectory 'detect-final.log') -Encoding utf8
        }
        if (Test-ContainerExists $replayContainer) {
            (Invoke-Docker -Arguments @('logs', $replayContainer) -AllowFailure $true) | Set-Content -LiteralPath (Join-Path $runDirectory 'replay-final.log') -Encoding utf8
        }
    } catch {
    }
    try { Stop-OwnedProcess -Process $worker } catch { }
    foreach ($name in @($detectContainer, $replayContainer, $postgresContainer)) {
        try { Stop-ContainerIfRunning -Name $name } catch { }
    }
    if ($transcriptStarted) { Stop-Transcript | Out-Null }
    if ($LeaveEvidenceContainersStopped -and (Test-Path -LiteralPath $runDirectory)) {
        Write-Host "Evidence containers were stopped but not removed. Run directory: $runDirectory"
    }
}
