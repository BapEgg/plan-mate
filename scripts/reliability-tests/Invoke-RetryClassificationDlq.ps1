#Requires -Version 7.0

[CmdletBinding()]
param(
    [int]$RequestCount = 10,
    [long]$UserId = 1,
    [string]$DestinationPlaceId = 'ChIJzWXFYYuifDUR64Pq5LTtioU',
    [string]$ApiBaseUrl = 'http://localhost:8080',
    [string]$WorkerBaseUrl = 'http://localhost:8081',
    [string]$RabbitMqManagementBaseUrl = 'http://localhost:15672',
    [int]$RecoveryTimeoutSeconds = 240,
    [int]$PrometheusSettleSeconds = 20
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repositoryRoot 'backend\.env'
$workerJar = Join-Path $repositoryRoot 'backend\build\libs\backend-0.0.1-SNAPSHOT.jar'
$runStartedAt = Get-Date
$runId = 'retry-classification-dlq-{0}' -f $runStartedAt.ToString('yyyyMMdd-HHmmss')
$runDirectory = Join-Path $repositoryRoot "docs\reliability-tests\runs\$runId"
$transcriptPath = Join-Path $runDirectory 'commands.log'
$worker = $null
$transcriptStarted = $false

$token = $runStartedAt.ToString('yyyyMMddHHmmss')
$experimentExchange = "planmate.reliability.exp4.$token"
$experimentQueue = "$experimentExchange.main"
$experimentRoutingKey = 'generation.requested'
$experimentDlx = "$experimentExchange.dlx"
$experimentDlq = "$experimentExchange.dlq"
$experimentDlqRoutingKey = 'generation.failed'
$productionQueue = 'planmate.itinerary.generation.requested'
$productionDlq = 'planmate.itinerary.generation.requested.dlq'

New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

function Write-Step([string]$Message) {
    Write-Host ('[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss.fff'), $Message)
}

function Add-Timeline([System.Collections.Generic.List[object]]$Timeline, [string]$Event, [string]$Detail) {
    $Timeline.Add([pscustomobject]@{
        event = $Event
        at = (Get-Date).ToUniversalTime().ToString('o')
        detail = $Detail
    })
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

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-TestAccessToken([string]$Secret, [long]$Subject) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
    $claimJson = @{
        sub = $Subject.ToString()
        role = 'USER'
        iat = $now
        exp = $now + 3600
    } | ConvertTo-Json -Compress
    $claims = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($claimJson))
    $unsignedToken = "$header.$claims"
    $hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsignedToken)))
    } finally {
        $hmac.Dispose()
    }
    return "$unsignedToken.$signature"
}

function Invoke-DatabaseScalar([string]$Sql) {
    $output = & docker exec planmate-postgres `
        psql -U $script:settings['POSTGRES_USER'] -d $script:settings['POSTGRES_DB'] -At -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "Database query failed with exit code $LASTEXITCODE"
    }
    return (($output | Out-String).Trim())
}

function New-RabbitHeaders {
    $credential = [Text.Encoding]::ASCII.GetBytes(
        $script:settings['RABBITMQ_USERNAME'] + ':' + $script:settings['RABBITMQ_PASSWORD']
    )
    return @{ Authorization = 'Basic ' + [Convert]::ToBase64String($credential) }
}

function Ensure-RabbitExchange([string]$Name, [hashtable]$Headers) {
    $encoded = [Uri]::EscapeDataString($Name)
    $body = @{
        type = 'direct'
        durable = $true
        auto_delete = $false
        internal = $false
        arguments = @{}
    } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Put `
        -Uri "$RabbitMqManagementBaseUrl/api/exchanges/%2F/$encoded" `
        -Headers $Headers -ContentType 'application/json' -Body $body | Out-Null
}

function Ensure-RabbitQueue(
    [string]$Name,
    [hashtable]$Headers,
    [string]$DeadLetterExchange = '',
    [string]$DeadLetterRoutingKey = ''
) {
    $encoded = [Uri]::EscapeDataString($Name)
    $arguments = @{}
    if (-not [string]::IsNullOrWhiteSpace($DeadLetterExchange)) {
        $arguments['x-dead-letter-exchange'] = $DeadLetterExchange
        $arguments['x-dead-letter-routing-key'] = $DeadLetterRoutingKey
    }
    $body = @{
        durable = $true
        auto_delete = $false
        arguments = $arguments
    } | ConvertTo-Json -Depth 5 -Compress
    Invoke-RestMethod -Method Put `
        -Uri "$RabbitMqManagementBaseUrl/api/queues/%2F/$encoded" `
        -Headers $Headers -ContentType 'application/json' -Body $body | Out-Null
}

function Ensure-RabbitBinding(
    [string]$Exchange,
    [string]$Queue,
    [string]$RoutingKey,
    [hashtable]$Headers
) {
    $encodedExchange = [Uri]::EscapeDataString($Exchange)
    $encodedQueue = [Uri]::EscapeDataString($Queue)
    $body = @{ routing_key = $RoutingKey; arguments = @{} } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Post `
        -Uri "$RabbitMqManagementBaseUrl/api/bindings/%2F/e/$encodedExchange/q/$encodedQueue" `
        -Headers $Headers -ContentType 'application/json' -Body $body | Out-Null
}

function Get-RabbitQueueSnapshot([string]$Name, [hashtable]$Headers) {
    $encoded = [Uri]::EscapeDataString($Name)
    $queue = Invoke-RestMethod `
        -Uri "$RabbitMqManagementBaseUrl/api/queues/%2F/$encoded" `
        -Headers $Headers -TimeoutSec 15
    return [ordered]@{
        name = $Name
        capturedAt = (Get-Date).ToUniversalTime().ToString('o')
        ready = [long]($queue.messages_ready ?? 0)
        unacked = [long]($queue.messages_unacknowledged ?? 0)
        total = [long]($queue.messages ?? 0)
        consumers = [long]($queue.consumers ?? 0)
        publish = [long]($queue.message_stats.publish ?? 0)
        deliver = [long]($queue.message_stats.deliver_get ?? 0)
        ack = [long]($queue.message_stats.ack ?? 0)
        redeliver = [long]($queue.message_stats.redeliver ?? 0)
    }
}

function Wait-QueueReady(
    [string]$Name,
    [long]$Expected,
    [hashtable]$Headers,
    [int]$TimeoutSeconds = 90
) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $snapshot = Get-RabbitQueueSnapshot -Name $Name -Headers $Headers
        if ($snapshot.ready -eq $Expected -and $snapshot.unacked -eq 0) {
            return $snapshot
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Queue $Name did not reach ready=$Expected and unacked=0; last ready=$($snapshot.ready), unacked=$($snapshot.unacked)"
}

function Wait-QueueDrained(
    [string]$Name,
    [hashtable]$Headers,
    [int]$TimeoutSeconds = 90
) {
    return Wait-QueueReady -Name $Name -Expected 0 -Headers $Headers -TimeoutSeconds $TimeoutSeconds
}

function Get-RabbitMessages(
    [string]$Name,
    [int]$Count,
    [hashtable]$Headers,
    [bool]$Requeue
) {
    $encoded = [Uri]::EscapeDataString($Name)
    $ackMode = if ($Requeue) { 'ack_requeue_true' } else { 'ack_requeue_false' }
    $body = @{
        count = $Count
        ackmode = $ackMode
        encoding = 'auto'
        truncate = 50000
    } | ConvertTo-Json -Compress
    $messages = Invoke-RestMethod -Method Post `
        -Uri "$RabbitMqManagementBaseUrl/api/queues/%2F/$encoded/get" `
        -Headers $Headers -ContentType 'application/json' -Body $body -TimeoutSec 30
    return @($messages)
}

function Publish-RabbitMessage(
    [object]$Message,
    [string]$Exchange,
    [string]$RoutingKey,
    [hashtable]$Headers
) {
    $encodedExchange = [Uri]::EscapeDataString($Exchange)
    $properties = @{}
    if ($null -ne $Message.properties) {
        foreach ($property in $Message.properties.PSObject.Properties) {
            $properties[$property.Name] = $property.Value
        }
    }
    $body = @{
        properties = $properties
        routing_key = $RoutingKey
        payload = [string]$Message.payload
        payload_encoding = 'string'
    } | ConvertTo-Json -Depth 20 -Compress
    $response = Invoke-RestMethod -Method Post `
        -Uri "$RabbitMqManagementBaseUrl/api/exchanges/%2F/$encodedExchange/publish" `
        -Headers $Headers -ContentType 'application/json' -Body $body -TimeoutSec 30
    if (-not $response.routed) {
        throw "RabbitMQ did not route message to $Exchange with routing key $RoutingKey"
    }
}

function Get-MessageGenerationIds([object[]]$Messages) {
    $ids = foreach ($message in $Messages) {
        $payload = $message.payload | ConvertFrom-Json
        [long]$payload.generationId
    }
    return [long[]]$ids
}

function Assert-SameIds([long[]]$Expected, [long[]]$Actual, [string]$Context) {
    $expectedText = (@($Expected | Sort-Object) -join ',')
    $actualText = (@($Actual | Sort-Object) -join ',')
    if ($expectedText -ne $actualText) {
        throw "$Context generation IDs differ. expected=[$expectedText], actual=[$actualText]"
    }
}

function Wait-HealthUp([string]$BaseUrl, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 3
            if ($health.status -eq 'UP') {
                return Get-Date
            }
        } catch {
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $null
}

function New-TripPayload([string]$Title, [string]$FreeRequest) {
    $startDate = (Get-Date).Date.AddDays(30)
    $endDate = $startDate.AddDays(1)
    return [ordered]@{
        title = $Title
        destinationPlaceId = $DestinationPlaceId
        startDate = $startDate.ToString('yyyy-MM-dd')
        endDate = $endDate.ToString('yyyy-MM-dd')
        companion = [ordered]@{
            count = 1
            type = 'SOLO'
            hasChildren = $false
            childCount = 0
            childAgeGroup = $null
            hasSeniors = $false
            seniorCount = 0
        }
        budget = [ordered]@{
            currencyCode = 'KRW'
            amount = 300000
            level = 'BALANCED'
            includedItems = @('FOOD', 'TRANSPORT')
        }
        preferences = [ordered]@{
            travelPace = 'BALANCED'
            interests = @('SIGHTSEEING', 'FOOD')
        }
        transportation = [ordered]@{
            primaryMode = 'PUBLIC_TRANSIT'
            secondaryModes = @('WALK')
        }
        accommodation = [ordered]@{
            mode = 'UNDECIDED'
            preferredArea = 'ANYWHERE'
            placeId = $null
            checkInTime = $null
            checkOutTime = $null
        }
        schedulePreference = [ordered]@{
            dailyStartTime = '09:00'
            dailyEndTime = '20:00'
        }
        additionalRequest = [ordered]@{
            mustVisitPlaceIds = @()
            avoidConditions = @()
            freeRequest = $FreeRequest
        }
    }
}

function New-GenerationBatch(
    [string]$NamePrefix,
    [string]$Description,
    [hashtable]$ApiHeaders
) {
    $items = [System.Collections.Generic.List[object]]::new()
    for ($index = 1; $index -le $RequestCount; $index++) {
        $testName = '{0}-{1:D2}' -f $NamePrefix, $index
        $payload = New-TripPayload -Title $testName -FreeRequest "$Description / $runId"
        $trip = Invoke-RestMethod -Method Post `
            -Uri "$ApiBaseUrl/api/trips" `
            -Headers $ApiHeaders -ContentType 'application/json' `
            -Body ($payload | ConvertTo-Json -Depth 10) -TimeoutSec 60
        $generation = Invoke-RestMethod -Method Post `
            -Uri "$ApiBaseUrl/api/trips/$($trip.id)/itinerary-generations" `
            -Headers $ApiHeaders -ContentType 'application/json' -Body '{}' -TimeoutSec 30
        $items.Add([pscustomobject]@{
            name = $testName
            tripId = [long]$trip.id
            generationId = [long]$generation.generationId
            initialStatus = $generation.status
            createdAt = (Get-Date).ToUniversalTime().ToString('o')
        })
        Write-Step "Created ${testName}: trip=$($trip.id), generation=$($generation.generationId)"
    }
    return @($items)
}

function Get-DatabaseSnapshot([long[]]$GenerationIds) {
    if ($GenerationIds.Count -eq 0) {
        throw 'GenerationIds must not be empty'
    }
    $idList = $GenerationIds -join ','
    $outboxIds = $GenerationIds | ForEach-Object { "'$_'" } | Join-String -Separator ','
    $sql = @"
SELECT json_build_object(
  'outboxCount', (SELECT count(*) FROM outbox_events WHERE aggregate_id IN ($outboxIds)),
  'createdCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'CREATED'),
  'collectingCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'COLLECTING_CANDIDATES'),
  'readyCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'READY_FOR_PLANNING'),
  'failedCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'FAILED'),
  'candidateCount', (SELECT count(*) FROM place_candidates WHERE generation_id IN ($idList)),
  'failureReasons', (SELECT COALESCE(json_object_agg(failure_reason, reason_count), '{}'::json) FROM (
      SELECT failure_reason, count(*) AS reason_count
      FROM itinerary_generations
      WHERE id IN ($idList) AND failure_reason IS NOT NULL
      GROUP BY failure_reason
  ) reasons)
)::text;
"@
    return (Invoke-DatabaseScalar $sql | ConvertFrom-Json)
}

function Wait-DatabaseFailed([long[]]$GenerationIds, [int]$Expected, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $snapshot = Get-DatabaseSnapshot -GenerationIds $GenerationIds
        if ([long]$snapshot.failedCount -eq $Expected) {
            return $snapshot
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Database did not reach FAILED=$Expected; last failed=$($snapshot.failedCount), collecting=$($snapshot.collectingCount)"
}

function Start-WorkerProcess([string]$Mode, [string]$LogPrefix) {
    $env:DB_URL = 'jdbc:postgresql://localhost:15432/planmate'
    $env:SPRING_DOCKER_COMPOSE_ENABLED = 'false'
    $env:SERVER_PORT = '8081'
    $env:APP_ITINERARY_MANUAL_HANDOFF_ENABLED = 'false'
    $env:APP_ITINERARY_GENERATION_WORKER_ENABLED = 'true'
    $env:APP_ITINERARY_GENERATION_WORKER_STALE_RECOVERY_ENABLED = 'false'
    $env:APP_ITINERARY_GENERATION_WORKER_MAX_ATTEMPTS = '3'
    $env:APP_ITINERARY_CANDIDATES_PROVIDER = 'reliability-failure'
    $env:APP_ITINERARY_CANDIDATE_RELIABILITY_FAILURE_MODE = $Mode
    $env:APP_ITINERARY_CANDIDATE_RELIABILITY_FAILURE_DELAY = '1s'
    $env:APP_ITINERARY_GENERATION_EXCHANGE = $experimentExchange
    $env:APP_ITINERARY_GENERATION_QUEUE = $experimentQueue
    $env:APP_ITINERARY_GENERATION_ROUTING_KEY = $experimentRoutingKey
    $env:APP_ITINERARY_GENERATION_DLX = $experimentDlx
    $env:APP_ITINERARY_GENERATION_DLQ = $experimentDlq
    $env:APP_ITINERARY_GENERATION_DLQ_ROUTING_KEY = $experimentDlqRoutingKey
    $env:SPRING_RABBITMQ_LISTENER_SIMPLE_PREFETCH = '1'

    $stdoutPath = Join-Path $runDirectory "$LogPrefix-worker.out.log"
    $stderrPath = Join-Path $runDirectory "$LogPrefix-worker.err.log"
    $javaPath = (Get-Command java).Source
    return Start-Process `
        -FilePath $javaPath `
        -ArgumentList @('-jar', $workerJar) `
        -WorkingDirectory $repositoryRoot `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -WindowStyle Hidden `
        -PassThru
}

function Stop-OwnedProcess($Process) {
    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit(15000) | Out-Null
    }
}

function Get-MetricValue(
    [string]$PrometheusText,
    [string]$MetricName,
    [hashtable]$RequiredLabels
) {
    foreach ($line in ($PrometheusText -split "`r?`n")) {
        if ($line -notmatch ('^' + [regex]::Escape($MetricName) + '(?:\{(?<labels>[^}]*)\})?\s+(?<value>[-+0-9.eE]+)$')) {
            continue
        }
        $labels = $Matches['labels']
        $matchesAll = $true
        foreach ($entry in $RequiredLabels.GetEnumerator()) {
            $needle = '{0}="{1}"' -f $entry.Key, $entry.Value
            if ($labels -notlike "*$needle*") {
                $matchesAll = $false
                break
            }
        }
        if ($matchesAll) {
            return [double]$Matches['value']
        }
    }
    return [double]0
}

function Save-WorkerEvidence([string]$Prefix) {
    $metrics = Invoke-RestMethod -Uri "$WorkerBaseUrl/actuator/prometheus" -TimeoutSec 15
    $metrics | Set-Content -LiteralPath (Join-Path $runDirectory "$Prefix-metrics.prom") -Encoding utf8
    return $metrics
}

function Move-ProductionMessagesToExperiment(
    [long[]]$ExpectedGenerationIds,
    [string]$ArtifactName,
    [hashtable]$RabbitHeaders
) {
    Wait-QueueReady -Name $productionQueue -Expected $RequestCount -Headers $RabbitHeaders -TimeoutSeconds 90 | Out-Null

    $peeked = Get-RabbitMessages -Name $productionQueue -Count $RequestCount -Headers $RabbitHeaders -Requeue $true
    if ($peeked.Count -ne $RequestCount) {
        throw "Expected to peek $RequestCount source messages, got $($peeked.Count)"
    }
    Assert-SameIds -Expected $ExpectedGenerationIds -Actual (Get-MessageGenerationIds $peeked) -Context 'Peeked production queue'
    Wait-QueueReady -Name $productionQueue -Expected $RequestCount -Headers $RabbitHeaders -TimeoutSeconds 30 | Out-Null

    $messages = Get-RabbitMessages -Name $productionQueue -Count $RequestCount -Headers $RabbitHeaders -Requeue $false
    if ($messages.Count -ne $RequestCount) {
        throw "Expected to take $RequestCount source messages, got $($messages.Count)"
    }
    Assert-SameIds -Expected $ExpectedGenerationIds -Actual (Get-MessageGenerationIds $messages) -Context 'Taken production queue'
    $messages | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $runDirectory $ArtifactName) -Encoding utf8

    foreach ($message in $messages) {
        Publish-RabbitMessage -Message $message -Exchange $experimentExchange `
            -RoutingKey $experimentRoutingKey -Headers $RabbitHeaders
    }
    Wait-QueueDrained -Name $productionQueue -Headers $RabbitHeaders -TimeoutSeconds 30 | Out-Null
    Wait-QueueReady -Name $experimentQueue -Expected $RequestCount -Headers $RabbitHeaders -TimeoutSeconds 30 | Out-Null
}

$settings = Read-DotEnv $envFile
foreach ($pair in $settings.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($pair.Key, $pair.Value, 'Process')
}
foreach ($requiredSetting in @('JWT_SECRET', 'RABBITMQ_USERNAME', 'RABBITMQ_PASSWORD', 'POSTGRES_USER', 'POSTGRES_DB')) {
    if (-not $settings.ContainsKey($requiredSetting) -or [string]::IsNullOrWhiteSpace($settings[$requiredSetting])) {
        throw "Required setting is missing: $requiredSetting"
    }
}
if (-not (Test-Path -LiteralPath $workerJar)) {
    throw "Worker JAR not found: $workerJar. Run backend/gradlew.bat -p backend bootJar first."
}

try {
    Start-Transcript -LiteralPath $transcriptPath | Out-Null
    $transcriptStarted = $true
    $timeline = [System.Collections.Generic.List[object]]::new()
    $databaseSnapshots = [ordered]@{}
    $rabbitSnapshots = [ordered]@{}

    Write-Step "Run directory: $runDirectory"
    Write-Step 'Checking API, Worker port, RabbitMQ and database prerequisites'
    $apiHealth = Invoke-RestMethod -Uri "$ApiBaseUrl/actuator/health" -TimeoutSec 10
    if ($apiHealth.status -ne 'UP') {
        throw "API health is not UP: $($apiHealth.status)"
    }
    if ($null -ne (Wait-HealthUp -BaseUrl $WorkerBaseUrl -TimeoutSeconds 2)) {
        throw 'Port 8081 already has a healthy Worker. Stop it before this experiment.'
    }

    $accessToken = New-TestAccessToken -Secret $settings['JWT_SECRET'] -Subject $UserId
    $apiHeaders = @{ Authorization = "Bearer $accessToken" }
    $rabbitHeaders = New-RabbitHeaders
    $authStatus = Invoke-RestMethod -Uri "$ApiBaseUrl/api/auth/status" -Headers $apiHeaders -TimeoutSec 10
    if (-not $authStatus.authenticated -or [long]$authStatus.user.id -ne $UserId) {
        throw "Local test user authentication failed for user $UserId"
    }

    $rabbitSnapshots.productionMainBefore = Get-RabbitQueueSnapshot -Name $productionQueue -Headers $rabbitHeaders
    $rabbitSnapshots.productionDlqBefore = Get-RabbitQueueSnapshot -Name $productionDlq -Headers $rabbitHeaders
    if ($rabbitSnapshots.productionMainBefore.ready -ne 0 -or $rabbitSnapshots.productionMainBefore.unacked -ne 0) {
        throw "Production main queue must start empty: ready=$($rabbitSnapshots.productionMainBefore.ready), unacked=$($rabbitSnapshots.productionMainBefore.unacked)"
    }
    if ($rabbitSnapshots.productionMainBefore.consumers -ne 0) {
        throw "Production main queue must have no consumers, got $($rabbitSnapshots.productionMainBefore.consumers)"
    }

    Ensure-RabbitExchange -Name $experimentExchange -Headers $rabbitHeaders
    Ensure-RabbitExchange -Name $experimentDlx -Headers $rabbitHeaders
    Ensure-RabbitQueue -Name $experimentQueue -Headers $rabbitHeaders `
        -DeadLetterExchange $experimentDlx -DeadLetterRoutingKey $experimentDlqRoutingKey
    Ensure-RabbitQueue -Name $experimentDlq -Headers $rabbitHeaders
    Ensure-RabbitBinding -Exchange $experimentExchange -Queue $experimentQueue `
        -RoutingKey $experimentRoutingKey -Headers $rabbitHeaders
    Ensure-RabbitBinding -Exchange $experimentDlx -Queue $experimentDlq `
        -RoutingKey $experimentDlqRoutingKey -Headers $rabbitHeaders

    $rabbitSnapshots.experimentMainBefore = Get-RabbitQueueSnapshot -Name $experimentQueue -Headers $rabbitHeaders
    $rabbitSnapshots.experimentDlqBefore = Get-RabbitQueueSnapshot -Name $experimentDlq -Headers $rabbitHeaders
    Add-Timeline -Timeline $timeline -Event 'T0_BASELINE_READY' `
        -Detail "API UP, Worker DOWN, production queue 0, production DLQ $($rabbitSnapshots.productionDlqBefore.ready)"

    Write-Step 'Phase A: creating 10 retryable-failure requests'
    $retryableItems = New-GenerationBatch `
        -NamePrefix '장애테스트-재시도가능실패' `
        -Description 'Retryable failure: 3 attempts then DLQ' `
        -ApiHeaders $apiHeaders
    $retryableIds = [long[]]$retryableItems.generationId
    $databaseSnapshots.retryableCreated = Get-DatabaseSnapshot -GenerationIds $retryableIds
    Add-Timeline -Timeline $timeline -Event 'T1_RETRYABLE_TEN_CREATED' -Detail "generationIds=$($retryableIds -join ',')"

    Write-Step 'Moving the exact retryable batch from the production queue to the isolated experiment queue'
    Move-ProductionMessagesToExperiment -ExpectedGenerationIds $retryableIds `
        -ArtifactName 'retryable-source-messages.json' -RabbitHeaders $rabbitHeaders
    $rabbitSnapshots.retryableBeforeWorker = Get-RabbitQueueSnapshot -Name $experimentQueue -Headers $rabbitHeaders
    Add-Timeline -Timeline $timeline -Event 'T2_RETRYABLE_ISOLATED_QUEUE_READY' -Detail 'ready=10, consumers=0'

    Write-Step 'Starting Retryable worker: max attempts=3, controlled provider failure=1s per attempt'
    $retryableWorkerStartedAt = Get-Date
    $worker = Start-WorkerProcess -Mode 'retryable' -LogPrefix 'retryable'
    $retryableWorkerHealthAt = Wait-HealthUp -BaseUrl $WorkerBaseUrl -TimeoutSeconds 90
    if ($null -eq $retryableWorkerHealthAt) {
        throw 'Retryable Worker did not become healthy within 90 seconds'
    }
    Add-Timeline -Timeline $timeline -Event 'T3_RETRYABLE_WORKER_UP' -Detail "pid=$($worker.Id)"

    $retryableDatabase = Wait-DatabaseFailed -GenerationIds $retryableIds `
        -Expected $RequestCount -TimeoutSeconds $RecoveryTimeoutSeconds
    $retryableFailedAt = Get-Date
    $rabbitSnapshots.retryableMainAfter = Wait-QueueDrained -Name $experimentQueue `
        -Headers $rabbitHeaders -TimeoutSeconds 45
    $rabbitSnapshots.retryableDlqAfter = Wait-QueueReady -Name $experimentDlq `
        -Expected $RequestCount -Headers $rabbitHeaders -TimeoutSeconds 45
    Add-Timeline -Timeline $timeline -Event 'T4_RETRYABLE_FAILED_AND_DLQ' `
        -Detail "FAILED=10, DLQ=10, elapsed=$([Math]::Round(($retryableFailedAt - $retryableWorkerStartedAt).TotalSeconds, 3))s"

    Start-Sleep -Seconds $PrometheusSettleSeconds
    $retryableMetrics = Save-WorkerEvidence -Prefix 'retryable'
    $retryableFailureAttempts = Get-MetricValue -PrometheusText $retryableMetrics `
        -MetricName 'planmate_itinerary_generation_worker_failure_attempt_total' `
        -RequiredLabels @{ classification = 'retryable'; failureCode = 'PLACE_PROVIDER_UNAVAILABLE' }
    $retryableRetries = Get-MetricValue -PrometheusText $retryableMetrics `
        -MetricName 'planmate_itinerary_generation_worker_retry_total' `
        -RequiredLabels @{ classification = 'retryable'; failureCode = 'PLACE_PROVIDER_UNAVAILABLE' }
    $retryableProcessed = Get-MetricValue -PrometheusText $retryableMetrics `
        -MetricName 'planmate_itinerary_generation_worker_processed_total' `
        -RequiredLabels @{ result = 'failed' }
    $retryableDeliveries = Get-MetricValue -PrometheusText $retryableMetrics `
        -MetricName 'planmate_itinerary_generation_worker_delivery_total' `
        -RequiredLabels @{ redelivered = 'false' }
    Stop-OwnedProcess $worker
    $worker = $null
    Add-Timeline -Timeline $timeline -Event 'T5_RETRYABLE_EVIDENCE_CAPTURED' `
        -Detail "attempts=$retryableFailureAttempts, retries=$retryableRetries, processed=$retryableProcessed"

    Write-Step 'Phase B: creating 10 non-retryable-failure requests'
    $nonRetryableItems = New-GenerationBatch `
        -NamePrefix '장애테스트-재시도불가실패' `
        -Description 'Non-retryable failure: 1 attempt then DLQ' `
        -ApiHeaders $apiHeaders
    $nonRetryableIds = [long[]]$nonRetryableItems.generationId
    $databaseSnapshots.nonRetryableCreated = Get-DatabaseSnapshot -GenerationIds $nonRetryableIds
    Add-Timeline -Timeline $timeline -Event 'T6_NON_RETRYABLE_TEN_CREATED' -Detail "generationIds=$($nonRetryableIds -join ',')"

    Write-Step 'Moving the exact non-retryable batch to the isolated experiment queue'
    Move-ProductionMessagesToExperiment -ExpectedGenerationIds $nonRetryableIds `
        -ArtifactName 'non-retryable-source-messages.json' -RabbitHeaders $rabbitHeaders
    $rabbitSnapshots.nonRetryableBeforeWorker = Get-RabbitQueueSnapshot -Name $experimentQueue -Headers $rabbitHeaders
    Add-Timeline -Timeline $timeline -Event 'T7_NON_RETRYABLE_ISOLATED_QUEUE_READY' -Detail 'ready=10, consumers=0'

    Write-Step 'Starting Non-Retryable worker: max attempts=3 but classification must stop after attempt 1'
    $nonRetryableWorkerStartedAt = Get-Date
    $worker = Start-WorkerProcess -Mode 'non-retryable' -LogPrefix 'non-retryable'
    $nonRetryableWorkerHealthAt = Wait-HealthUp -BaseUrl $WorkerBaseUrl -TimeoutSeconds 90
    if ($null -eq $nonRetryableWorkerHealthAt) {
        throw 'Non-Retryable Worker did not become healthy within 90 seconds'
    }
    Add-Timeline -Timeline $timeline -Event 'T8_NON_RETRYABLE_WORKER_UP' -Detail "pid=$($worker.Id)"

    $nonRetryableDatabase = Wait-DatabaseFailed -GenerationIds $nonRetryableIds `
        -Expected $RequestCount -TimeoutSeconds $RecoveryTimeoutSeconds
    $nonRetryableFailedAt = Get-Date
    $rabbitSnapshots.nonRetryableMainAfter = Wait-QueueDrained -Name $experimentQueue `
        -Headers $rabbitHeaders -TimeoutSeconds 45
    $rabbitSnapshots.nonRetryableDlqAfter = Wait-QueueReady -Name $experimentDlq `
        -Expected ($RequestCount * 2) -Headers $rabbitHeaders -TimeoutSeconds 45
    Add-Timeline -Timeline $timeline -Event 'T9_NON_RETRYABLE_FAILED_AND_DLQ' `
        -Detail "FAILED=10, DLQ=20, elapsed=$([Math]::Round(($nonRetryableFailedAt - $nonRetryableWorkerStartedAt).TotalSeconds, 3))s"

    Start-Sleep -Seconds $PrometheusSettleSeconds
    $nonRetryableMetrics = Save-WorkerEvidence -Prefix 'non-retryable'
    $nonRetryableFailureAttempts = Get-MetricValue -PrometheusText $nonRetryableMetrics `
        -MetricName 'planmate_itinerary_generation_worker_failure_attempt_total' `
        -RequiredLabels @{ classification = 'non_retryable'; failureCode = 'PLACE_PROVIDER_REQUEST_REJECTED' }
    $nonRetryableRetries = Get-MetricValue -PrometheusText $nonRetryableMetrics `
        -MetricName 'planmate_itinerary_generation_worker_retry_total' `
        -RequiredLabels @{ classification = 'non_retryable'; failureCode = 'PLACE_PROVIDER_REQUEST_REJECTED' }
    $nonRetryableProcessed = Get-MetricValue -PrometheusText $nonRetryableMetrics `
        -MetricName 'planmate_itinerary_generation_worker_processed_total' `
        -RequiredLabels @{ result = 'failed' }
    $nonRetryableDeliveries = Get-MetricValue -PrometheusText $nonRetryableMetrics `
        -MetricName 'planmate_itinerary_generation_worker_delivery_total' `
        -RequiredLabels @{ redelivered = 'false' }
    Stop-OwnedProcess $worker
    $worker = $null
    Add-Timeline -Timeline $timeline -Event 'T10_NON_RETRYABLE_EVIDENCE_CAPTURED' `
        -Detail "attempts=$nonRetryableFailureAttempts, retries=$nonRetryableRetries, processed=$nonRetryableProcessed"

    $allIds = [long[]]@($retryableIds + $nonRetryableIds)
    $dlqMessages = Get-RabbitMessages -Name $experimentDlq -Count ($RequestCount * 2) `
        -Headers $rabbitHeaders -Requeue $true
    $dlqMessages | ConvertTo-Json -Depth 40 | Set-Content `
        -LiteralPath (Join-Path $runDirectory 'dlq-messages.json') -Encoding utf8
    $dlqGenerationIds = Get-MessageGenerationIds $dlqMessages
    Assert-SameIds -Expected $allIds -Actual $dlqGenerationIds -Context 'Experiment DLQ'
    $rabbitSnapshots.experimentDlqFinal = Wait-QueueReady -Name $experimentDlq `
        -Expected ($RequestCount * 2) -Headers $rabbitHeaders -TimeoutSeconds 30
    $rabbitSnapshots.productionMainAfter = Get-RabbitQueueSnapshot -Name $productionQueue -Headers $rabbitHeaders
    $rabbitSnapshots.productionDlqAfter = Get-RabbitQueueSnapshot -Name $productionDlq -Headers $rabbitHeaders
    $databaseSnapshots.retryableFinal = $retryableDatabase
    $databaseSnapshots.nonRetryableFinal = $nonRetryableDatabase

    $retryableReasonCount = [long]($retryableDatabase.failureReasons.PLACE_PROVIDER_UNAVAILABLE ?? 0)
    $nonRetryableReasonCount = [long]($nonRetryableDatabase.failureReasons.PLACE_PROVIDER_REQUEST_REJECTED ?? 0)
    $productionDlqDelta = [long]$rabbitSnapshots.productionDlqAfter.ready - [long]$rabbitSnapshots.productionDlqBefore.ready
    $checks = [ordered]@{
        retryableTenFailed = [long]$retryableDatabase.failedCount -eq $RequestCount
        retryableNoReady = [long]$retryableDatabase.readyCount -eq 0
        retryableNoCandidates = [long]$retryableDatabase.candidateCount -eq 0
        retryableStableFailureReason = $retryableReasonCount -eq $RequestCount
        retryableThirtyFailureAttempts = [long]$retryableFailureAttempts -eq ($RequestCount * 3)
        retryableTwentyRetries = [long]$retryableRetries -eq ($RequestCount * 2)
        retryableTenProcessedFailures = [long]$retryableProcessed -eq $RequestCount
        retryableTenDeliveries = [long]$retryableDeliveries -eq $RequestCount
        retryableDlqTen = [long]$rabbitSnapshots.retryableDlqAfter.ready -eq $RequestCount
        nonRetryableTenFailed = [long]$nonRetryableDatabase.failedCount -eq $RequestCount
        nonRetryableNoReady = [long]$nonRetryableDatabase.readyCount -eq 0
        nonRetryableNoCandidates = [long]$nonRetryableDatabase.candidateCount -eq 0
        nonRetryableStableFailureReason = $nonRetryableReasonCount -eq $RequestCount
        nonRetryableTenFailureAttempts = [long]$nonRetryableFailureAttempts -eq $RequestCount
        nonRetryableZeroRetries = [long]$nonRetryableRetries -eq 0
        nonRetryableTenProcessedFailures = [long]$nonRetryableProcessed -eq $RequestCount
        nonRetryableTenDeliveries = [long]$nonRetryableDeliveries -eq $RequestCount
        finalDlqTwenty = [long]$rabbitSnapshots.experimentDlqFinal.ready -eq ($RequestCount * 2)
        dlqContainsExactlyTwentyExperimentIds = $dlqMessages.Count -eq ($RequestCount * 2)
        experimentMainQueueDrained = [long]$rabbitSnapshots.nonRetryableMainAfter.ready -eq 0 -and [long]$rabbitSnapshots.nonRetryableMainAfter.unacked -eq 0
        productionMainQueueDrained = [long]$rabbitSnapshots.productionMainAfter.ready -eq 0 -and [long]$rabbitSnapshots.productionMainAfter.unacked -eq 0
        productionDlqUnchanged = $productionDlqDelta -eq 0
    }
    $failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object Key)
    $verdict = if ($failedChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }
    Add-Timeline -Timeline $timeline -Event 'T11_FINAL_VERDICT' `
        -Detail "$verdict; retryable attempts=$retryableFailureAttempts/retries=$retryableRetries; non-retryable attempts=$nonRetryableFailureAttempts/retries=$nonRetryableRetries; DLQ=$($rabbitSnapshots.experimentDlqFinal.ready)"

    $commitSha = (& git -C $repositoryRoot rev-parse HEAD).Trim()
    $result = [ordered]@{
        experimentId = $runId
        experimentName = 'Retry 분류와 DLQ 격리'
        startedAt = $runStartedAt.ToUniversalTime().ToString('o')
        finishedAt = (Get-Date).ToUniversalTime().ToString('o')
        commitSha = $commitSha
        requestCountPerScenario = $RequestCount
        experimentExchange = $experimentExchange
        experimentQueue = $experimentQueue
        experimentDlq = $experimentDlq
        productionDlqBaseline = [long]$rabbitSnapshots.productionDlqBefore.ready
        productionDlqFinal = [long]$rabbitSnapshots.productionDlqAfter.ready
        productionDlqDelta = $productionDlqDelta
        retryable = [ordered]@{
            testNames = @($retryableItems.name)
            tripIds = @($retryableItems.tripId)
            generationIds = @($retryableIds)
            expectedAttempts = $RequestCount * 3
            actualAttempts = [long]$retryableFailureAttempts
            expectedRetries = $RequestCount * 2
            actualRetries = [long]$retryableRetries
            failed = [long]$retryableDatabase.failedCount
            ready = [long]$retryableDatabase.readyCount
            candidates = [long]$retryableDatabase.candidateCount
            failureCode = 'PLACE_PROVIDER_UNAVAILABLE'
            failureReasonCount = $retryableReasonCount
            dlqAfter = [long]$rabbitSnapshots.retryableDlqAfter.ready
            processingSeconds = [Math]::Round(($retryableFailedAt - $retryableWorkerStartedAt).TotalSeconds, 3)
        }
        nonRetryable = [ordered]@{
            testNames = @($nonRetryableItems.name)
            tripIds = @($nonRetryableItems.tripId)
            generationIds = @($nonRetryableIds)
            expectedAttempts = $RequestCount
            actualAttempts = [long]$nonRetryableFailureAttempts
            expectedRetries = 0
            actualRetries = [long]$nonRetryableRetries
            failed = [long]$nonRetryableDatabase.failedCount
            ready = [long]$nonRetryableDatabase.readyCount
            candidates = [long]$nonRetryableDatabase.candidateCount
            failureCode = 'PLACE_PROVIDER_REQUEST_REJECTED'
            failureReasonCount = $nonRetryableReasonCount
            dlqAfter = [long]$rabbitSnapshots.nonRetryableDlqAfter.ready
            processingSeconds = [Math]::Round(($nonRetryableFailedAt - $nonRetryableWorkerStartedAt).TotalSeconds, 3)
        }
        totalDlq = [long]$rabbitSnapshots.experimentDlqFinal.ready
        dlqGenerationIds = @($dlqGenerationIds | Sort-Object)
        checks = $checks
        failedChecks = $failedChecks
        verdict = $verdict
    }

    $result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDirectory 'result.json') -Encoding utf8
    [ordered]@{
        retryable = $retryableItems
        nonRetryable = $nonRetryableItems
    } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'created-items.json') -Encoding utf8
    $databaseSnapshots | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $runDirectory 'database-snapshots.json') -Encoding utf8
    $rabbitSnapshots | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $runDirectory 'rabbitmq-snapshots.json') -Encoding utf8
    $timeline | Export-Csv -LiteralPath (Join-Path $runDirectory 'timeline.csv') -NoTypeInformation -Encoding utf8

    $queries = @"
-- Experiment: $runId
-- Retryable generation IDs: $($retryableIds -join ', ')
-- Non-Retryable generation IDs: $($nonRetryableIds -join ', ')
SELECT id, trip_id, status, failure_reason, collection_claim_version, created_at, updated_at
FROM itinerary_generations
WHERE id IN ($($allIds -join ','))
ORDER BY id;

SELECT status, failure_reason, count(*) AS count
FROM itinerary_generations
WHERE id IN ($($allIds -join ','))
GROUP BY status, failure_reason
ORDER BY failure_reason;

SELECT generation_id, count(*) AS candidate_count
FROM place_candidates
WHERE generation_id IN ($($allIds -join ','))
GROUP BY generation_id
ORDER BY generation_id;
"@
    $queries | Set-Content -LiteralPath (Join-Path $runDirectory 'queries.sql') -Encoding utf8

    $summary = @"
# Retry 분류와 DLQ 격리 결과

- 실행 ID: $runId
- 시나리오별 요청: ${RequestCount}건
- 전용 실험 Queue: $($experimentQueue)
- 전용 실험 DLQ: $($experimentDlq)
- 판정: **$verdict**

## 핵심 결과

| 구분 | 기대값 | 실제값 |
| --- | ---: | ---: |
| Retryable 실패 시도 | $($RequestCount * 3) | $retryableFailureAttempts |
| Retryable Retry | $($RequestCount * 2) | $retryableRetries |
| Retryable FAILED / DLQ | $RequestCount / $RequestCount | $($retryableDatabase.failedCount) / $($rabbitSnapshots.retryableDlqAfter.ready) |
| Non-Retryable 실패 시도 | $RequestCount | $nonRetryableFailureAttempts |
| Non-Retryable Retry | 0 | $nonRetryableRetries |
| Non-Retryable FAILED / 누적 DLQ | $RequestCount / $($RequestCount * 2) | $($nonRetryableDatabase.failedCount) / $($rabbitSnapshots.nonRetryableDlqAfter.ready) |
| 기존 운영 DLQ 변화 | 0 | $productionDlqDelta |

실패한 검사: $(if ($failedChecks.Count -eq 0) { '없음' } else { $failedChecks -join ', ' })
"@
    $summary | Set-Content -LiteralPath (Join-Path $runDirectory 'README.md') -Encoding utf8

    Write-Step "Experiment verdict: $verdict"
    Write-Step "Result: $(Join-Path $runDirectory 'result.json')"
    $result | ConvertTo-Json -Depth 20
} finally {
    Stop-OwnedProcess $worker
    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }
}
