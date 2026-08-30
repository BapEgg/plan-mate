#Requires -Version 7.0

[CmdletBinding()]
param(
    [int]$RequestCount = 10,
    [long]$UserId = 1,
    [string]$DestinationPlaceId = 'ChIJzWXFYYuifDUR64Pq5LTtioU',
    [string]$ApiBaseUrl = 'http://localhost:8080',
    [string]$WorkerABaseUrl = 'http://localhost:8081',
    [string]$WorkerBBaseUrl = 'http://localhost:8082',
    [string]$RabbitMqManagementBaseUrl = 'http://localhost:15672',
    [int]$ProcessingLeaseSeconds = 15,
    [int]$WorkerADelaySeconds = 120,
    [int]$RecoveryScanSeconds = 10,
    [int]$TimeoutSeconds = 240,
    [int]$PrometheusSettleSeconds = 20
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repositoryRoot 'backend\.env'
$workerJar = Join-Path $repositoryRoot 'backend\build\libs\backend-0.0.1-SNAPSHOT.jar'
$runStartedAt = Get-Date
$runId = 'stale-generation-recovery-{0}' -f $runStartedAt.ToString('yyyyMMdd-HHmmss')
$runDirectory = Join-Path $repositoryRoot "docs\reliability-tests\runs\$runId"
$transcriptPath = Join-Path $runDirectory 'commands.log'
$transcriptStarted = $false
$workerA = $null
$workerB = $null

$token = $runStartedAt.ToString('yyyyMMddHHmmss')
$experimentExchange = "planmate.reliability.exp5.$token"
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
        type = 'direct'; durable = $true; auto_delete = $false; internal = $false; arguments = @{}
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
    $body = @{ durable = $true; auto_delete = $false; arguments = $arguments } |
        ConvertTo-Json -Depth 5 -Compress
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

function Wait-Queue(
    [string]$Name,
    [hashtable]$Headers,
    [scriptblock]$Condition,
    [string]$Description,
    [int]$WaitSeconds = 90
) {
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    do {
        $snapshot = Get-RabbitQueueSnapshot -Name $Name -Headers $Headers
        if (& $Condition $snapshot) {
            return $snapshot
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Queue $Name did not reach $Description; last ready=$($snapshot.ready), unacked=$($snapshot.unacked), consumers=$($snapshot.consumers)"
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
        count = $Count; ackmode = $ackMode; encoding = 'auto'; truncate = 50000
    } | ConvertTo-Json -Compress
    return @(Invoke-RestMethod -Method Post `
        -Uri "$RabbitMqManagementBaseUrl/api/queues/%2F/$encoded/get" `
        -Headers $Headers -ContentType 'application/json' -Body $body -TimeoutSec 30)
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
    return [long[]]@($Messages | ForEach-Object { [long](($_.payload | ConvertFrom-Json).generationId) })
}

function Assert-SameIds([long[]]$Expected, [long[]]$Actual, [string]$Context) {
    $expectedText = (@($Expected | Sort-Object) -join ',')
    $actualText = (@($Actual | Sort-Object) -join ',')
    if ($expectedText -ne $actualText) {
        throw "$Context IDs differ. expected=[$expectedText], actual=[$actualText]"
    }
}

function Wait-HealthUp([string]$BaseUrl, [int]$WaitSeconds) {
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
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
            count = 1; type = 'SOLO'; hasChildren = $false; childCount = 0
            childAgeGroup = $null; hasSeniors = $false; seniorCount = 0
        }
        budget = [ordered]@{
            currencyCode = 'KRW'; amount = 300000; level = 'BALANCED'
            includedItems = @('FOOD', 'TRANSPORT')
        }
        preferences = [ordered]@{ travelPace = 'BALANCED'; interests = @('SIGHTSEEING', 'FOOD') }
        transportation = [ordered]@{ primaryMode = 'PUBLIC_TRANSIT'; secondaryModes = @('WALK') }
        accommodation = [ordered]@{
            mode = 'UNDECIDED'; preferredArea = 'ANYWHERE'; placeId = $null
            checkInTime = $null; checkOutTime = $null
        }
        schedulePreference = [ordered]@{ dailyStartTime = '09:00'; dailyEndTime = '20:00' }
        additionalRequest = [ordered]@{
            mustVisitPlaceIds = @(); avoidConditions = @(); freeRequest = $FreeRequest
        }
    }
}

function New-GenerationBatch([hashtable]$ApiHeaders) {
    $items = [System.Collections.Generic.List[object]]::new()
    for ($index = 1; $index -le $RequestCount; $index++) {
        $testName = '장애테스트-스테일복구-{0:D2}' -f $index
        $payload = New-TripPayload -Title $testName -FreeRequest "Stale recovery and fencing / $runId"
        $trip = Invoke-RestMethod -Method Post `
            -Uri "$ApiBaseUrl/api/trips" -Headers $ApiHeaders -ContentType 'application/json' `
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
    $idList = $GenerationIds -join ','
    $outboxIds = $GenerationIds | ForEach-Object { "'$_'" } | Join-String -Separator ','
    $sql = @"
SELECT json_build_object(
  'capturedAt', now(),
  'outboxCount', (SELECT count(*) FROM outbox_events WHERE aggregate_id IN ($outboxIds)),
  'createdCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'CREATED'),
  'collectingCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'COLLECTING_CANDIDATES'),
  'readyCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'READY_FOR_PLANNING'),
  'failedCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'FAILED'),
  'claimVersionOneCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND collection_claim_version = 1),
  'claimVersionTwoCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND collection_claim_version = 2),
  'minClaimVersion', (SELECT min(collection_claim_version) FROM itinerary_generations WHERE id IN ($idList)),
  'maxClaimVersion', (SELECT max(collection_claim_version) FROM itinerary_generations WHERE id IN ($idList)),
  'leaseExpiredCollectingCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'COLLECTING_CANDIDATES' AND collection_lease_expires_at <= now()),
  'earliestLeaseExpiresAt', (SELECT min(collection_lease_expires_at) FROM itinerary_generations WHERE id IN ($idList)),
  'latestLeaseExpiresAt', (SELECT max(collection_lease_expires_at) FROM itinerary_generations WHERE id IN ($idList)),
  'candidateCount', (SELECT count(*) FROM place_candidates WHERE generation_id IN ($idList)),
  'distinctGenerationPlaceCount', (SELECT count(DISTINCT (generation_id, place_id)) FROM place_candidates WHERE generation_id IN ($idList)),
  'distinctGenerationRankCount', (SELECT count(DISTINCT (generation_id, rank)) FROM place_candidates WHERE generation_id IN ($idList)),
  'minCandidatesPerGeneration', (SELECT COALESCE(min(candidate_count), 0) FROM (SELECT count(*) AS candidate_count FROM place_candidates WHERE generation_id IN ($idList) GROUP BY generation_id) c),
  'maxCandidatesPerGeneration', (SELECT COALESCE(max(candidate_count), 0) FROM (SELECT count(*) AS candidate_count FROM place_candidates WHERE generation_id IN ($idList) GROUP BY generation_id) c)
)::text;
"@
    return (Invoke-DatabaseScalar $sql | ConvertFrom-Json)
}

function Wait-Database(
    [long[]]$GenerationIds,
    [scriptblock]$Condition,
    [string]$Description,
    [int]$WaitSeconds
) {
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    do {
        $snapshot = Get-DatabaseSnapshot -GenerationIds $GenerationIds
        if (& $Condition $snapshot) {
            return $snapshot
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Database did not reach $Description; last created=$($snapshot.createdCount), collecting=$($snapshot.collectingCount), ready=$($snapshot.readyCount), claim1=$($snapshot.claimVersionOneCount), claim2=$($snapshot.claimVersionTwoCount), expired=$($snapshot.leaseExpiredCollectingCount)"
}

function Set-WorkerEnvironment(
    [int]$Port,
    [bool]$StaleRecoveryEnabled,
    [int]$DeterministicDelaySeconds
) {
    $env:DB_URL = 'jdbc:postgresql://localhost:15432/planmate'
    $env:SPRING_DOCKER_COMPOSE_ENABLED = 'false'
    $env:SERVER_PORT = $Port.ToString()
    $env:APP_ITINERARY_MANUAL_HANDOFF_ENABLED = 'false'
    $env:APP_ITINERARY_GENERATION_WORKER_ENABLED = 'true'
    $env:APP_ITINERARY_GENERATION_WORKER_STALE_RECOVERY_ENABLED = $StaleRecoveryEnabled.ToString().ToLowerInvariant()
    $env:APP_ITINERARY_GENERATION_WORKER_MAX_ATTEMPTS = '1'
    $env:APP_ITINERARY_GENERATION_WORKER_PROCESSING_LEASE = "${ProcessingLeaseSeconds}s"
    $env:APP_ITINERARY_GENERATION_WORKER_RECOVERY_SCAN_INTERVAL = "${RecoveryScanSeconds}s"
    $env:APP_ITINERARY_GENERATION_WORKER_RECOVERY_BATCH_SIZE = $RequestCount.ToString()
    $env:APP_ITINERARY_CANDIDATES_PROVIDER = 'deterministic'
    $env:APP_ITINERARY_CANDIDATE_DETERMINISTIC_DELAY = "${DeterministicDelaySeconds}s"
    $env:APP_ITINERARY_GENERATION_EXCHANGE = $experimentExchange
    $env:APP_ITINERARY_GENERATION_QUEUE = $experimentQueue
    $env:APP_ITINERARY_GENERATION_ROUTING_KEY = $experimentRoutingKey
    $env:APP_ITINERARY_GENERATION_DLX = $experimentDlx
    $env:APP_ITINERARY_GENERATION_DLQ = $experimentDlq
    $env:APP_ITINERARY_GENERATION_DLQ_ROUTING_KEY = $experimentDlqRoutingKey
    $env:SPRING_RABBITMQ_LISTENER_SIMPLE_PREFETCH = '1'
    $env:SPRING_RABBITMQ_LISTENER_SIMPLE_CONCURRENCY = $RequestCount.ToString()
    $env:SPRING_RABBITMQ_LISTENER_SIMPLE_MAX_CONCURRENCY = $RequestCount.ToString()
}

function Start-WorkerProcess(
    [string]$WorkerName,
    [int]$Port,
    [bool]$StaleRecoveryEnabled,
    [int]$DeterministicDelaySeconds
) {
    Set-WorkerEnvironment -Port $Port -StaleRecoveryEnabled $StaleRecoveryEnabled `
        -DeterministicDelaySeconds $DeterministicDelaySeconds
    $stdoutPath = Join-Path $runDirectory "$WorkerName.out.log"
    $stderrPath = Join-Path $runDirectory "$WorkerName.err.log"
    return Start-Process -FilePath ((Get-Command java).Source) `
        -ArgumentList @('-jar', $workerJar) -WorkingDirectory $repositoryRoot `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath `
        -WindowStyle Hidden -PassThru
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
            if ($labels -notlike ('*{0}="{1}"*' -f $entry.Key, $entry.Value)) {
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

function Save-Metrics([string]$BaseUrl, [string]$Name) {
    $metrics = Invoke-RestMethod -Uri "$BaseUrl/actuator/prometheus" -TimeoutSec 15
    $metrics | Set-Content -LiteralPath (Join-Path $runDirectory "$Name.prom") -Encoding utf8
    return $metrics
}

function Move-ProductionMessagesToExperiment(
    [long[]]$GenerationIds,
    [hashtable]$RabbitHeaders
) {
    Wait-Queue -Name $productionQueue -Headers $RabbitHeaders `
        -Condition { param($q) $q.ready -eq $RequestCount -and $q.unacked -eq 0 } `
        -Description "ready=$RequestCount, unacked=0" -WaitSeconds 90 | Out-Null
    $peeked = Get-RabbitMessages -Name $productionQueue -Count $RequestCount `
        -Headers $RabbitHeaders -Requeue $true
    Assert-SameIds -Expected $GenerationIds -Actual (Get-MessageGenerationIds $peeked) `
        -Context 'Peeked production queue'
    Wait-Queue -Name $productionQueue -Headers $RabbitHeaders `
        -Condition { param($q) $q.ready -eq $RequestCount -and $q.unacked -eq 0 } `
        -Description "requeued ready=$RequestCount" -WaitSeconds 30 | Out-Null
    $messages = Get-RabbitMessages -Name $productionQueue -Count $RequestCount `
        -Headers $RabbitHeaders -Requeue $false
    Assert-SameIds -Expected $GenerationIds -Actual (Get-MessageGenerationIds $messages) `
        -Context 'Taken production queue'
    $messages | ConvertTo-Json -Depth 30 | Set-Content `
        -LiteralPath (Join-Path $runDirectory 'source-messages.json') -Encoding utf8
    foreach ($message in $messages) {
        Publish-RabbitMessage -Message $message -Exchange $experimentExchange `
            -RoutingKey $experimentRoutingKey -Headers $RabbitHeaders
    }
    Wait-Queue -Name $experimentQueue -Headers $RabbitHeaders `
        -Condition { param($q) $q.ready -eq $RequestCount -and $q.unacked -eq 0 } `
        -Description "experiment ready=$RequestCount" -WaitSeconds 30 | Out-Null
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
    throw "Worker JAR not found: $workerJar"
}

try {
    Start-Transcript -LiteralPath $transcriptPath | Out-Null
    $transcriptStarted = $true
    $timeline = [System.Collections.Generic.List[object]]::new()
    $databaseSnapshots = [ordered]@{}
    $rabbitSnapshots = [ordered]@{}

    Write-Step "Run directory: $runDirectory"
    Write-Step 'Checking API, Worker ports, RabbitMQ and database prerequisites'
    if ((Invoke-RestMethod -Uri "$ApiBaseUrl/actuator/health" -TimeoutSec 10).status -ne 'UP') {
        throw 'API is not UP'
    }
    if ($null -ne (Wait-HealthUp -BaseUrl $WorkerABaseUrl -WaitSeconds 2)) {
        throw 'Worker A port 8081 is already in use'
    }
    if ($null -ne (Wait-HealthUp -BaseUrl $WorkerBBaseUrl -WaitSeconds 2)) {
        throw 'Worker B port 8082 is already in use'
    }

    $accessToken = New-TestAccessToken -Secret $settings['JWT_SECRET'] -Subject $UserId
    $apiHeaders = @{ Authorization = "Bearer $accessToken" }
    $rabbitHeaders = New-RabbitHeaders
    $authStatus = Invoke-RestMethod -Uri "$ApiBaseUrl/api/auth/status" -Headers $apiHeaders -TimeoutSec 10
    if (-not $authStatus.authenticated -or [long]$authStatus.user.id -ne $UserId) {
        throw "Authentication failed for user $UserId"
    }

    $rabbitSnapshots.productionMainBefore = Get-RabbitQueueSnapshot -Name $productionQueue -Headers $rabbitHeaders
    $rabbitSnapshots.productionDlqBefore = Get-RabbitQueueSnapshot -Name $productionDlq -Headers $rabbitHeaders
    if ($rabbitSnapshots.productionMainBefore.ready -ne 0 -or $rabbitSnapshots.productionMainBefore.unacked -ne 0 -or $rabbitSnapshots.productionMainBefore.consumers -ne 0) {
        throw "Production queue is not isolated: ready=$($rabbitSnapshots.productionMainBefore.ready), unacked=$($rabbitSnapshots.productionMainBefore.unacked), consumers=$($rabbitSnapshots.productionMainBefore.consumers)"
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
        -Detail "API UP, Worker A/B DOWN, production queue 0, production DLQ $($rabbitSnapshots.productionDlqBefore.ready)"

    Write-Step 'Creating 10 stale-recovery test requests'
    $createdItems = New-GenerationBatch -ApiHeaders $apiHeaders
    $generationIds = [long[]]$createdItems.generationId
    $databaseSnapshots.created = Get-DatabaseSnapshot -GenerationIds $generationIds
    Add-Timeline -Timeline $timeline -Event 'T1_TEN_REQUESTS_CREATED' `
        -Detail "generationIds=$($generationIds -join ',')"

    Write-Step 'Moving exact 10 messages to the isolated experiment queue'
    Move-ProductionMessagesToExperiment -GenerationIds $generationIds -RabbitHeaders $rabbitHeaders
    $rabbitSnapshots.beforeWorkerA = Get-RabbitQueueSnapshot -Name $experimentQueue -Headers $rabbitHeaders
    Add-Timeline -Timeline $timeline -Event 'T2_EXPERIMENT_QUEUE_READY' -Detail 'ready=10, unacked=0'

    Write-Step "Starting Worker A with 10 consumers and ${WorkerADelaySeconds}s controlled candidate delay"
    $workerAStartedAt = Get-Date
    $workerA = Start-WorkerProcess -WorkerName 'worker-a' -Port 8081 `
        -StaleRecoveryEnabled $false -DeterministicDelaySeconds $WorkerADelaySeconds
    $workerAHealthAt = Wait-HealthUp -BaseUrl $WorkerABaseUrl -WaitSeconds 90
    if ($null -eq $workerAHealthAt) {
        throw 'Worker A did not become healthy'
    }
    Add-Timeline -Timeline $timeline -Event 'T3_WORKER_A_UP' -Detail "pid=$($workerA.Id)"

    $databaseSnapshots.claimVersionOne = Wait-Database -GenerationIds $generationIds `
        -Condition { param($db) [long]$db.collectingCount -eq $RequestCount -and [long]$db.claimVersionOneCount -eq $RequestCount } `
        -Description "COLLECTING=$RequestCount and Claim Version 1=$RequestCount" -WaitSeconds 60
    $rabbitSnapshots.workerAHolding = Wait-Queue -Name $experimentQueue -Headers $rabbitHeaders `
        -Condition { param($q) $q.ready -eq 0 -and $q.unacked -eq $RequestCount -and $q.consumers -eq $RequestCount } `
        -Description "ready=0, unacked=$RequestCount, consumers=$RequestCount" -WaitSeconds 45
    $workerAClaimsAt = Get-Date
    Add-Timeline -Timeline $timeline -Event 'T4_WORKER_A_CLAIMED_TEN' `
        -Detail "COLLECTING=10, claimVersion=1, queue unacked=10"
    $workerABeforeExpiryMetrics = Save-Metrics -BaseUrl $WorkerABaseUrl -Name 'worker-a-before-expiry-metrics'

    Write-Step "Waiting for all ${ProcessingLeaseSeconds}s leases to expire while Worker A remains healthy"
    $databaseSnapshots.leaseExpired = Wait-Database -GenerationIds $generationIds `
        -Condition { param($db) [long]$db.leaseExpiredCollectingCount -eq $RequestCount } `
        -Description "expired COLLECTING lease=$RequestCount" -WaitSeconds 60
    $leaseExpiredObservedAt = Get-Date
    Add-Timeline -Timeline $timeline -Event 'T5_ALL_LEASES_EXPIRED' `
        -Detail "expired=10, Worker A health=UP, queue unacked=10"

    Write-Step "Starting Worker B with stale recovery scheduler every ${RecoveryScanSeconds}s"
    $workerBStartedAt = Get-Date
    $workerB = Start-WorkerProcess -WorkerName 'worker-b' -Port 8082 `
        -StaleRecoveryEnabled $true -DeterministicDelaySeconds 0
    $workerBHealthAt = Wait-HealthUp -BaseUrl $WorkerBBaseUrl -WaitSeconds 90
    if ($null -eq $workerBHealthAt) {
        throw 'Worker B did not become healthy'
    }
    Add-Timeline -Timeline $timeline -Event 'T6_WORKER_B_UP' -Detail "pid=$($workerB.Id)"

    $databaseSnapshots.recoveredReady = Wait-Database -GenerationIds $generationIds `
        -Condition { param($db) [long]$db.readyCount -eq $RequestCount -and [long]$db.claimVersionTwoCount -eq $RequestCount -and [long]$db.candidateCount -eq ($RequestCount * 120) } `
        -Description "READY=$RequestCount, Claim Version 2=$RequestCount, candidates=$($RequestCount * 120)" `
        -WaitSeconds $TimeoutSeconds
    $recoveryCompletedAt = Get-Date
    Add-Timeline -Timeline $timeline -Event 'T7_WORKER_B_RECOVERY_COMPLETED' `
        -Detail "READY=10, claimVersion=2, candidates=1200"

    $rabbitSnapshots.afterWorkerBRecovery = Get-RabbitQueueSnapshot -Name $experimentQueue -Headers $rabbitHeaders
    if ($rabbitSnapshots.afterWorkerBRecovery.unacked -ne $RequestCount) {
        throw "Expected Worker A originals to remain unacked=$RequestCount after B recovery, got $($rabbitSnapshots.afterWorkerBRecovery.unacked)"
    }

    Write-Step 'Waiting for delayed Worker A results to return and be fenced'
    $fencingDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $workerAMetrics = Invoke-RestMethod -Uri "$WorkerABaseUrl/actuator/prometheus" -TimeoutSec 15
        $fencedCount = Get-MetricValue -PrometheusText $workerAMetrics `
            -MetricName 'planmate_itinerary_generation_worker_fenced_total' `
            -RequiredLabels @{ operation = 'candidate_save' }
        if ([long]$fencedCount -eq $RequestCount) {
            break
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $fencingDeadline)
    if ([long]$fencedCount -ne $RequestCount) {
        throw "Worker A fenced count did not reach $RequestCount; last=$fencedCount"
    }
    $workerAFencedAt = Get-Date
    $rabbitSnapshots.finalMain = Wait-Queue -Name $experimentQueue -Headers $rabbitHeaders `
        -Condition { param($q) $q.ready -eq 0 -and $q.unacked -eq 0 } `
        -Description 'ready=0, unacked=0' -WaitSeconds 45
    Add-Timeline -Timeline $timeline -Event 'T8_WORKER_A_RESULTS_FENCED' `
        -Detail "fenced=10, queue ready=0, unacked=0"

    Start-Sleep -Seconds $PrometheusSettleSeconds
    $workerAMetrics = Save-Metrics -BaseUrl $WorkerABaseUrl -Name 'worker-a-final-metrics'
    $workerBMetrics = Save-Metrics -BaseUrl $WorkerBBaseUrl -Name 'worker-b-final-metrics'
    $databaseSnapshots.final = Get-DatabaseSnapshot -GenerationIds $generationIds
    $rabbitSnapshots.finalDlq = Get-RabbitQueueSnapshot -Name $experimentDlq -Headers $rabbitHeaders
    $rabbitSnapshots.productionMainAfter = Get-RabbitQueueSnapshot -Name $productionQueue -Headers $rabbitHeaders
    $rabbitSnapshots.productionDlqAfter = Get-RabbitQueueSnapshot -Name $productionDlq -Headers $rabbitHeaders

    $initialClaims = Get-MetricValue -PrometheusText $workerAMetrics `
        -MetricName 'planmate_itinerary_generation_worker_claim_total' `
        -RequiredLabels @{ type = 'initial' }
    $workerASkipped = Get-MetricValue -PrometheusText $workerAMetrics `
        -MetricName 'planmate_itinerary_generation_worker_processed_total' `
        -RequiredLabels @{ result = 'skipped' }
    $recoveryPublished = Get-MetricValue -PrometheusText $workerBMetrics `
        -MetricName 'planmate_itinerary_generation_worker_recovery_publish_total' `
        -RequiredLabels @{}
    $recoveryClaims = Get-MetricValue -PrometheusText $workerBMetrics `
        -MetricName 'planmate_itinerary_generation_worker_claim_total' `
        -RequiredLabels @{ type = 'recovery' }
    $workerBSuccess = Get-MetricValue -PrometheusText $workerBMetrics `
        -MetricName 'planmate_itinerary_generation_worker_processed_total' `
        -RequiredLabels @{ result = 'success' }

    $candidateDuplicates = [long]$databaseSnapshots.final.candidateCount - [long]$databaseSnapshots.final.distinctGenerationPlaceCount
    $productionDlqDelta = [long]$rabbitSnapshots.productionDlqAfter.ready - [long]$rabbitSnapshots.productionDlqBefore.ready
    $publishDelta = [long]$rabbitSnapshots.finalMain.publish - [long]$rabbitSnapshots.experimentMainBefore.publish
    $deliverDelta = [long]$rabbitSnapshots.finalMain.deliver - [long]$rabbitSnapshots.experimentMainBefore.deliver
    $ackDelta = [long]$rabbitSnapshots.finalMain.ack - [long]$rabbitSnapshots.experimentMainBefore.ack

    $checks = [ordered]@{
        workerAClaimedTenVersionOne = [long]$initialClaims -eq $RequestCount -and [long]$databaseSnapshots.claimVersionOne.claimVersionOneCount -eq $RequestCount
        workerAHeldTenUnacked = [long]$rabbitSnapshots.workerAHolding.unacked -eq $RequestCount
        allTenLeasesExpired = [long]$databaseSnapshots.leaseExpired.leaseExpiredCollectingCount -eq $RequestCount
        recoveryPublishedExactlyTen = [long]$recoveryPublished -eq $RequestCount
        workerBClaimedTenVersionTwo = [long]$recoveryClaims -eq $RequestCount -and [long]$databaseSnapshots.final.claimVersionTwoCount -eq $RequestCount
        workerBProcessedTenSuccessfully = [long]$workerBSuccess -eq $RequestCount
        lateWorkerAResultsFencedTen = [long]$fencedCount -eq $RequestCount
        lateWorkerAProcessedAsSkippedTen = [long]$workerASkipped -eq $RequestCount
        allReady = [long]$databaseSnapshots.final.readyCount -eq $RequestCount
        noFailed = [long]$databaseSnapshots.final.failedCount -eq 0
        exactlyOneCandidateSetPerGeneration = [long]$databaseSnapshots.final.candidateCount -eq ($RequestCount * 120) -and $candidateDuplicates -eq 0 -and [long]$databaseSnapshots.final.minCandidatesPerGeneration -eq 120 -and [long]$databaseSnapshots.final.maxCandidatesPerGeneration -eq 120
        experimentPublishedInitialPlusRecovery = $publishDelta -eq ($RequestCount * 2)
        experimentDeliveredTwenty = $deliverDelta -eq ($RequestCount * 2)
        experimentAckedTwenty = $ackDelta -eq ($RequestCount * 2)
        experimentMainQueueDrained = [long]$rabbitSnapshots.finalMain.ready -eq 0 -and [long]$rabbitSnapshots.finalMain.unacked -eq 0
        experimentDlqEmpty = [long]$rabbitSnapshots.finalDlq.ready -eq 0
        productionMainQueueDrained = [long]$rabbitSnapshots.productionMainAfter.ready -eq 0 -and [long]$rabbitSnapshots.productionMainAfter.unacked -eq 0
        productionDlqUnchanged = $productionDlqDelta -eq 0
    }
    $failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object Key)
    $verdict = if ($failedChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }
    Add-Timeline -Timeline $timeline -Event 'T9_FINAL_VERDICT' `
        -Detail "$verdict; initialClaims=$initialClaims, recoveryPublished=$recoveryPublished, recoveryClaims=$recoveryClaims, fenced=$fencedCount, READY=$($databaseSnapshots.final.readyCount)"

    $result = [ordered]@{
        experimentId = $runId
        experimentName = 'Worker 장기 정지와 Stale Generation 자동 복구'
        startedAt = $runStartedAt.ToUniversalTime().ToString('o')
        finishedAt = (Get-Date).ToUniversalTime().ToString('o')
        commitSha = (& git -C $repositoryRoot rev-parse HEAD).Trim()
        requestedTrips = $RequestCount
        testNames = @($createdItems.name)
        tripIds = @($createdItems.tripId)
        generationIds = @($generationIds)
        processingLeaseSeconds = $ProcessingLeaseSeconds
        workerADelaySeconds = $WorkerADelaySeconds
        recoveryScanSeconds = $RecoveryScanSeconds
        experimentExchange = $experimentExchange
        experimentQueue = $experimentQueue
        experimentDlq = $experimentDlq
        workerAPid = $workerA.Id
        workerBPid = $workerB.Id
        initialClaims = [long]$initialClaims
        expiredLeases = [long]$databaseSnapshots.leaseExpired.leaseExpiredCollectingCount
        recoveryPublished = [long]$recoveryPublished
        recoveryClaims = [long]$recoveryClaims
        finalClaimVersion = [long]$databaseSnapshots.final.maxClaimVersion
        fencedLateResults = [long]$fencedCount
        workerASkipped = [long]$workerASkipped
        workerBSuccess = [long]$workerBSuccess
        readyForPlanning = [long]$databaseSnapshots.final.readyCount
        failed = [long]$databaseSnapshots.final.failedCount
        candidateRows = [long]$databaseSnapshots.final.candidateCount
        duplicateCandidateRows = $candidateDuplicates
        rabbitMqPublishDelta = $publishDelta
        rabbitMqDeliverDelta = $deliverDelta
        rabbitMqAckDelta = $ackDelta
        experimentDlqFinal = [long]$rabbitSnapshots.finalDlq.ready
        productionDlqBaseline = [long]$rabbitSnapshots.productionDlqBefore.ready
        productionDlqFinal = [long]$rabbitSnapshots.productionDlqAfter.ready
        productionDlqDelta = $productionDlqDelta
        leaseExpiryWaitSeconds = [Math]::Round(($leaseExpiredObservedAt - $workerAClaimsAt).TotalSeconds, 3)
        recoveryAfterLeaseExpirySeconds = [Math]::Round(($recoveryCompletedAt - $leaseExpiredObservedAt).TotalSeconds, 3)
        workerBStartToReadySeconds = [Math]::Round(($recoveryCompletedAt - $workerBStartedAt).TotalSeconds, 3)
        lateResultFencingAfterReadySeconds = [Math]::Round(($workerAFencedAt - $recoveryCompletedAt).TotalSeconds, 3)
        workerAStartToFencedSeconds = [Math]::Round(($workerAFencedAt - $workerAStartedAt).TotalSeconds, 3)
        checks = $checks
        failedChecks = $failedChecks
        verdict = $verdict
    }

    $result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDirectory 'result.json') -Encoding utf8
    $createdItems | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'created-items.json') -Encoding utf8
    $databaseSnapshots | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $runDirectory 'database-snapshots.json') -Encoding utf8
    $rabbitSnapshots | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $runDirectory 'rabbitmq-snapshots.json') -Encoding utf8
    $timeline | Export-Csv -LiteralPath (Join-Path $runDirectory 'timeline.csv') -NoTypeInformation -Encoding utf8

    $queries = @"
-- Experiment: $runId
SELECT id, trip_id, status, collection_claim_version, collection_lease_expires_at, failure_reason, created_at, updated_at
FROM itinerary_generations
WHERE id IN ($($generationIds -join ','))
ORDER BY id;

SELECT generation_id, count(*) AS candidate_count,
       count(DISTINCT place_id) AS distinct_places,
       count(DISTINCT rank) AS distinct_ranks
FROM place_candidates
WHERE generation_id IN ($($generationIds -join ','))
GROUP BY generation_id
ORDER BY generation_id;
"@
    $queries | Set-Content -LiteralPath (Join-Path $runDirectory 'queries.sql') -Encoding utf8

    $summary = @"
# Worker 장기 정지와 Stale Generation 자동 복구 결과

- 실행 ID: $runId
- 요청: ${RequestCount}건
- Lease: ${ProcessingLeaseSeconds}초
- Worker A 지연: ${WorkerADelaySeconds}초
- 전용 Queue: $($experimentQueue)
- 판정: **$verdict**

| 항목 | 기대 | 실제 |
| --- | ---: | ---: |
| Worker A 초기 Claim Version 1 | 10 | $initialClaims |
| Lease 만료 | 10 | $($databaseSnapshots.leaseExpired.leaseExpiredCollectingCount) |
| Recovery Publish | 10 | $recoveryPublished |
| Worker B 복구 Claim Version 2 | 10 | $recoveryClaims |
| Worker B READY | 10 | $($databaseSnapshots.final.readyCount) |
| Worker A 늦은 결과 Fencing | 10 | $fencedCount |
| Candidate / 중복 | 1,200 / 0 | $($databaseSnapshots.final.candidateCount) / $candidateDuplicates |
| DLQ 증가 | 0 | $productionDlqDelta |

실패한 검사: $(if ($failedChecks.Count -eq 0) { '없음' } else { $failedChecks -join ', ' })
"@
    $summary | Set-Content -LiteralPath (Join-Path $runDirectory 'README.md') -Encoding utf8

    Write-Step "Experiment verdict: $verdict"
    Write-Step "Result: $(Join-Path $runDirectory 'result.json')"
    $result | ConvertTo-Json -Depth 20
} finally {
    Stop-OwnedProcess $workerA
    Stop-OwnedProcess $workerB
    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }
}
