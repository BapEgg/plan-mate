#Requires -Version 7.0

[CmdletBinding()]
param(
    [int]$RequestCount = 10,
    [long]$UserId = 1,
    [string]$DestinationPlaceId = 'ChIJzWXFYYuifDUR64Pq5LTtioU',
    [string]$ApiBaseUrl = 'http://localhost:8080',
    [string]$PrometheusBaseUrl = 'http://localhost:9090',
    [string]$RabbitMqManagementBaseUrl = 'http://localhost:15672',
    [int]$RecoveryTimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repositoryRoot 'backend\.env'
$composeFile = Join-Path $repositoryRoot 'infra\compose.local.yaml'
$runStartedAt = Get-Date
$runId = 'rabbitmq-outage-recovery-{0}' -f $runStartedAt.ToString('yyyyMMdd-HHmmss')
$runDirectory = Join-Path $repositoryRoot "docs\reliability-tests\runs\$runId"
$transcriptPath = Join-Path $runDirectory 'commands.log'
$rabbitMqWasStopped = $false
$transcriptStarted = $false
$auditQueueName = 'planmate.reliability.exp6.{0}.audit' -f $runStartedAt.ToString('yyyyMMddHHmmss')
$workerJar = Join-Path $repositoryRoot 'backend\build\libs\backend-0.0.1-SNAPSHOT.jar'
$workerProcess = $null

New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

function Write-Step([string]$Message) {
    Write-Host ('[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss.fff'), $Message)
}

function Protect-PublicEvidence {
    $extensions = @('.log', '.json', '.md', '.csv', '.sql')
    $workspaceForwardSlash = $repositoryRoot.Replace('\', '/')
    $localUser = [Environment]::UserName
    $machineName = [Environment]::MachineName

    Get-ChildItem -LiteralPath $runDirectory -File | Where-Object {
        $extensions -contains $_.Extension.ToLowerInvariant()
    } | ForEach-Object {
        $text = Get-Content -Raw -LiteralPath $_.FullName
        $text = $text.Replace($repositoryRoot, '<WORKSPACE>')
        $text = $text.Replace($workspaceForwardSlash, '<WORKSPACE>')
        $text = $text.Replace($localUser, '<LOCAL_USER>')
        $text = $text.Replace($machineName, '<LOCAL_MACHINE>')
        $text = $text.Replace('C:\Users\<LOCAL_USER>', '<USER_HOME>')
        $text = $text.Replace('C:/Users/<LOCAL_USER>', '<USER_HOME>')
        Set-Content -LiteralPath $_.FullName -Value $text -Encoding utf8 -NoNewline
    }
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
        exp = $now + 900
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

function Invoke-Compose([string[]]$Arguments) {
    & docker compose --env-file $envFile -f $composeFile --profile cdc --profile monitoring @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Invoke-Docker([string[]]$Arguments, [bool]$AllowFailure = $false) {
    $output = & docker @Arguments 2>&1
    if (-not $AllowFailure -and $LASTEXITCODE -ne 0) {
        throw "docker failed with exit code ${LASTEXITCODE}: docker $($Arguments -join ' ')`n$($output | Out-String)"
    }
    return $output
}

function Invoke-DatabaseScalar([string]$Sql) {
    $output = & docker compose --env-file $envFile -f $composeFile exec -T postgres `
        psql -U $script:settings['POSTGRES_USER'] -d $script:settings['POSTGRES_DB'] -At -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "Database query failed with exit code $LASTEXITCODE"
    }
    return (($output | Out-String).Trim())
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
            # Process or dependency is still starting.
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Start-IsolatedWorker {
    $env:DB_URL = 'jdbc:postgresql://localhost:15432/planmate'
    $env:SPRING_DOCKER_COMPOSE_ENABLED = 'false'
    $env:SERVER_PORT = '8081'
    $env:APP_ITINERARY_MANUAL_HANDOFF_ENABLED = 'false'
    $env:APP_ITINERARY_GENERATION_WORKER_ENABLED = 'true'
    $env:APP_ITINERARY_GENERATION_WORKER_STALE_RECOVERY_ENABLED = 'false'
    $env:APP_ITINERARY_CANDIDATES_PROVIDER = 'deterministic'
    $env:APP_ITINERARY_CANDIDATE_DETERMINISTIC_DELAY = '0s'
    $env:SPRING_RABBITMQ_LISTENER_SIMPLE_PREFETCH = '1'
    $env:SPRING_RABBITMQ_LISTENER_SIMPLE_CONCURRENCY = '1'
    $env:SPRING_RABBITMQ_LISTENER_SIMPLE_MAX_CONCURRENCY = '1'

    return Start-Process -FilePath ((Get-Command java).Source) `
        -ArgumentList @('-jar', $workerJar) `
        -WorkingDirectory $repositoryRoot `
        -RedirectStandardOutput (Join-Path $runDirectory 'worker.out.log') `
        -RedirectStandardError (Join-Path $runDirectory 'worker.err.log') `
        -WindowStyle Hidden `
        -PassThru
}

function Stop-OwnedProcess($Process) {
    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit(15000) | Out-Null
    }
}

function Get-NumberOrZero($Value) {
    if ($null -eq $Value -or "$Value" -eq '') {
        return [long]0
    }
    return [long]$Value
}

function Get-RabbitQueueSnapshot([string]$QueueName, [hashtable]$Headers) {
    $encodedQueue = [Uri]::EscapeDataString($QueueName)
    $queue = Invoke-RestMethod `
        -Uri "$RabbitMqManagementBaseUrl/api/queues/%2F/$encodedQueue" `
        -Headers $Headers `
        -TimeoutSec 15

    return [ordered]@{
        name = $QueueName
        capturedAt = (Get-Date).ToUniversalTime().ToString('o')
        ready = Get-NumberOrZero $queue.messages_ready
        unacked = Get-NumberOrZero $queue.messages_unacknowledged
        total = Get-NumberOrZero $queue.messages
        consumers = Get-NumberOrZero $queue.consumers
        publish = Get-NumberOrZero $queue.message_stats.publish
        deliver = Get-NumberOrZero $queue.message_stats.deliver_get
        ack = Get-NumberOrZero $queue.message_stats.ack
        redeliver = Get-NumberOrZero $queue.message_stats.redeliver
    }
}

function New-AuditQueue([string]$QueueName, [hashtable]$Headers) {
    $encodedQueue = [Uri]::EscapeDataString($QueueName)
    Invoke-RestMethod `
        -Method Put `
        -Uri "$RabbitMqManagementBaseUrl/api/queues/%2F/$encodedQueue" `
        -Headers $Headers `
        -ContentType 'application/json' `
        -Body '{"auto_delete":false,"durable":true,"arguments":{}}' `
        -TimeoutSec 15 | Out-Null

    $exchange = [Uri]::EscapeDataString('planmate.itinerary')
    Invoke-RestMethod `
        -Method Post `
        -Uri "$RabbitMqManagementBaseUrl/api/bindings/%2F/e/$exchange/q/$encodedQueue" `
        -Headers $Headers `
        -ContentType 'application/json' `
        -Body '{"routing_key":"itinerary.generation.requested","arguments":{}}' `
        -TimeoutSec 15 | Out-Null
}

function Get-RabbitMessages([string]$QueueName, [int]$Count, [hashtable]$Headers) {
    $encodedQueue = [Uri]::EscapeDataString($QueueName)
    $body = [ordered]@{
        count = $Count
        ackmode = 'ack_requeue_true'
        encoding = 'auto'
        truncate = 50000
    } | ConvertTo-Json -Compress
    return @(
        Invoke-RestMethod `
            -Method Post `
            -Uri "$RabbitMqManagementBaseUrl/api/queues/%2F/$encodedQueue/get" `
            -Headers $Headers `
            -ContentType 'application/json' `
            -Body $body `
            -TimeoutSec 20
    )
}

function Test-RabbitMqUp([hashtable]$Headers) {
    try {
        $overview = Invoke-RestMethod -Uri "$RabbitMqManagementBaseUrl/api/overview" -Headers $Headers -TimeoutSec 3
        return $null -ne $overview.rabbitmq_version
    } catch {
        return $false
    }
}

function Wait-RabbitMqUp([hashtable]$Headers, [bool]$Expected, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $actual = Test-RabbitMqUp -Headers $Headers
        if ($actual -eq $Expected) {
            return Get-Date
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Get-PrometheusUp([string]$Job) {
    $query = [Uri]::EscapeDataString("up{job=`"$Job`"}")
    $response = Invoke-RestMethod -Uri "$PrometheusBaseUrl/api/v1/query?query=$query" -TimeoutSec 15
    if ($response.status -ne 'success' -or $response.data.result.Count -eq 0) {
        return $null
    }
    return [double]$response.data.result[0].value[1]
}

function Wait-PrometheusUp([string]$Job, [double]$Expected, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $value = Get-PrometheusUp -Job $Job
            if ($null -ne $value -and $value -eq $Expected) {
                return Get-Date
            }
        } catch {
            Write-Step "Prometheus query retry: $($_.Exception.Message)"
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Get-PrometheusRange([string]$Query, [datetime]$Start, [datetime]$End) {
    $encoded = [Uri]::EscapeDataString($Query)
    $startSeconds = [DateTimeOffset]$Start.ToUniversalTime()
    $endSeconds = [DateTimeOffset]$End.ToUniversalTime()
    $uri = "$PrometheusBaseUrl/api/v1/query_range?query=$encoded&start=$($startSeconds.ToUnixTimeSeconds())&end=$($endSeconds.ToUnixTimeSeconds())&step=5"
    return Invoke-RestMethod -Uri $uri -TimeoutSec 30
}

function Get-DebeziumStatus {
    try {
        $health = Invoke-RestMethod -Uri 'http://localhost:8083/q/health' -TimeoutSec 3
        return [string]$health.status
    } catch {
        return 'UNREACHABLE'
    }
}

function Wait-DebeziumStatus([string[]]$Expected, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $status = Get-DebeziumStatus
        if ($Expected -contains $status) {
            return [pscustomobject]@{ at = Get-Date; status = $status }
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Get-OffsetSnapshot {
    $output = Invoke-Docker -Arguments @(
        'exec', 'planmate-debezium', 'sh', '-c',
        'sha256sum /debezium/data/offsets.dat; stat -c "%Y|%s" /debezium/data/offsets.dat'
    )
    $lines = @($output | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
    $hash = ($lines[0] -split '\s+')[0]
    $stat = $lines[1] -split '\|'
    return [ordered]@{
        capturedAt = (Get-Date).ToUniversalTime().ToString('o')
        sha256 = $hash
        modifiedEpoch = [long]$stat[0]
        bytes = [long]$stat[1]
    }
}

function Get-ReplicationSnapshot {
    $sql = @"
SELECT json_build_object(
  'capturedAt', now(),
  'currentWalLsn', pg_current_wal_lsn(),
  'slotName', slot_name,
  'restartLsn', restart_lsn,
  'confirmedFlushLsn', confirmed_flush_lsn,
  'active', active
)::text
FROM pg_replication_slots
WHERE slot_name = '$($script:settings['DEBEZIUM_SLOT_NAME'])';
"@
    $value = Invoke-DatabaseScalar $sql
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Replication slot not found: $($script:settings['DEBEZIUM_SLOT_NAME'])"
    }
    return $value | ConvertFrom-Json
}

function Get-DatabaseSnapshot([long[]]$GenerationIds) {
    if ($GenerationIds.Count -eq 0) {
        throw 'GenerationIds must not be empty'
    }
    $idList = $GenerationIds -join ','
    $aggregateIds = $GenerationIds | ForEach-Object { "'$_'" } | Join-String -Separator ','
    $sql = @"
SELECT json_build_object(
  'capturedAt', now(),
  'outboxCount', (SELECT count(*) FROM outbox_events WHERE aggregate_id IN ($aggregateIds)),
  'createdCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'CREATED'),
  'collectingCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'COLLECTING_CANDIDATES'),
  'readyCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'READY_FOR_PLANNING'),
  'failedCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'FAILED'),
  'candidateCount', (SELECT count(*) FROM place_candidates WHERE generation_id IN ($idList)),
  'distinctGenerationPlaceCount', (SELECT count(DISTINCT (generation_id, place_id)) FROM place_candidates WHERE generation_id IN ($idList)),
  'distinctGenerationRankCount', (SELECT count(DISTINCT (generation_id, rank)) FROM place_candidates WHERE generation_id IN ($idList))
)::text;
"@
    return (Invoke-DatabaseScalar $sql | ConvertFrom-Json)
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

function Get-Generation([long]$TripId, [long]$GenerationId, [hashtable]$Headers) {
    return Invoke-RestMethod `
        -Uri "$ApiBaseUrl/api/trips/$TripId/itinerary-generations/$GenerationId" `
        -Headers $Headers `
        -TimeoutSec 15
}

function Get-RestartAwareDelta($After, $Before, [string]$Property) {
    $afterValue = [long]$After[$Property]
    $beforeValue = [long]$Before[$Property]
    if ($afterValue -ge $beforeValue) {
        return $afterValue - $beforeValue
    }
    return $afterValue
}

function ConvertTo-Seconds([datetime]$End, [datetime]$Start) {
    return [Math]::Round(($End - $Start).TotalSeconds, 3)
}

$settings = Read-DotEnv $envFile
foreach ($pair in $settings.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($pair.Key, $pair.Value, 'Process')
}
foreach ($requiredSetting in @(
    'JWT_SECRET', 'RABBITMQ_USERNAME', 'RABBITMQ_PASSWORD',
    'POSTGRES_USER', 'POSTGRES_DB', 'DEBEZIUM_SLOT_NAME'
)) {
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
    Write-Step "Run directory: $runDirectory"
    Write-Step 'Checking prerequisites'

    $backendHealth = Invoke-RestMethod -Uri "$ApiBaseUrl/actuator/health" -TimeoutSec 10
    if ($backendHealth.status -ne 'UP') {
        throw "Backend health is not UP before experiment: $($backendHealth.status)"
    }
    if ((Get-DebeziumStatus) -ne 'UP') {
        throw 'Debezium must be UP before the experiment'
    }

    $accessToken = New-TestAccessToken -Secret $settings['JWT_SECRET'] -Subject $UserId
    $apiHeaders = @{ Authorization = "Bearer $accessToken" }
    $rabbitCredential = [Text.Encoding]::ASCII.GetBytes(
        $settings['RABBITMQ_USERNAME'] + ':' + $settings['RABBITMQ_PASSWORD']
    )
    $rabbitHeaders = @{ Authorization = 'Basic ' + [Convert]::ToBase64String($rabbitCredential) }

    if (-not (Test-RabbitMqUp -Headers $rabbitHeaders)) {
        throw 'RabbitMQ Management must be reachable before the experiment'
    }
    $authStatus = Invoke-RestMethod -Uri "$ApiBaseUrl/api/auth/status" -Headers $apiHeaders -TimeoutSec 10
    if (-not $authStatus.authenticated -or [long]$authStatus.user.id -ne $UserId) {
        throw "Local test user authentication failed for user $UserId"
    }

    $existingTripsJson = Invoke-DatabaseScalar @"
SELECT COALESCE(
  json_agg(json_build_object('id', id, 'title', title) ORDER BY title),
  '[]'::json
)::text
FROM trips
WHERE title LIKE '장애테스트-래빗엠큐중단-%';
"@
    $existingTrips = @($existingTripsJson | ConvertFrom-Json)
    if ($existingTrips.Count -notin @(0, $RequestCount)) {
        throw "Expected zero or $RequestCount reusable RabbitMQ outage trips, got $($existingTrips.Count)"
    }

    $mainQueueName = 'planmate.itinerary.generation.requested'
    $dlqName = 'planmate.itinerary.generation.requested.dlq'
    $rabbitBefore = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
    $dlqBefore = Get-RabbitQueueSnapshot -QueueName $dlqName -Headers $rabbitHeaders
    if ($rabbitBefore.ready -ne 0 -or $rabbitBefore.unacked -ne 0) {
        throw "Main queue is not empty: ready=$($rabbitBefore.ready), unacked=$($rabbitBefore.unacked)"
    }
    if ($rabbitBefore.consumers -ne 0) {
        throw "Expected no production Worker before isolated Worker start, got $($rabbitBefore.consumers)"
    }

    Write-Step 'Starting isolated deterministic Worker on port 8081'
    $workerProcess = Start-IsolatedWorker
    $workerHealthAt = Wait-HealthUp -BaseUrl 'http://localhost:8081' -TimeoutSeconds 90
    if ($null -eq $workerHealthAt) {
        throw 'Isolated Worker did not become healthy within 90 seconds'
    }
    $consumerDeadline = (Get-Date).AddSeconds(30)
    do {
        $rabbitBefore = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
        if ($rabbitBefore.consumers -eq 1) { break }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $consumerDeadline)
    if ($rabbitBefore.consumers -ne 1) {
        throw "Expected one isolated Worker consumer, got $($rabbitBefore.consumers)"
    }

    Write-Step "Creating experiment audit queue: $auditQueueName"
    New-AuditQueue -QueueName $auditQueueName -Headers $rabbitHeaders
    $auditBefore = Get-RabbitQueueSnapshot -QueueName $auditQueueName -Headers $rabbitHeaders
    $offsetBefore = Get-OffsetSnapshot
    $slotBefore = Get-ReplicationSnapshot

    $timeline = [System.Collections.Generic.List[object]]::new()
    $timeline.Add([pscustomobject]@{ event = 'T0_BASELINE_ALL_UP'; at = (Get-Date).ToUniversalTime().ToString('o') })

    Write-Step 'Stopping RabbitMQ only'
    $tStop = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T1_RABBITMQ_STOP'; at = $tStop.ToUniversalTime().ToString('o') })
    Invoke-Compose @('stop', 'rabbitmq')
    $rabbitMqWasStopped = $true

    $rabbitDownAt = Wait-RabbitMqUp -Headers $rabbitHeaders -Expected $false -TimeoutSeconds 30
    if ($null -eq $rabbitDownAt) {
        throw 'RabbitMQ Management did not become unreachable'
    }
    $timeline.Add([pscustomobject]@{ event = 'T2_RABBITMQ_DOWN'; at = $rabbitDownAt.ToUniversalTime().ToString('o') })

    $prometheusRabbitDownAt = Wait-PrometheusUp -Job 'rabbitmq' -Expected 0 -TimeoutSeconds 45
    if ($null -ne $prometheusRabbitDownAt) {
        $timeline.Add([pscustomobject]@{ event = 'PROMETHEUS_RABBITMQ_DOWN'; at = $prometheusRabbitDownAt.ToUniversalTime().ToString('o') })
    }

    $createdItems = [System.Collections.Generic.List[object]]::new()
    for ($index = 1; $index -le $RequestCount; $index++) {
        $testName = '장애테스트-래빗엠큐중단-{0:D2}' -f $index
        if ($existingTrips.Count -eq 0) {
            $payload = New-TripPayload -Title $testName -FreeRequest "RabbitMQ outage run $runId"
            $trip = Invoke-RestMethod `
                -Method Post `
                -Uri "$ApiBaseUrl/api/trips" `
                -Headers $apiHeaders `
                -ContentType 'application/json' `
                -Body ($payload | ConvertTo-Json -Depth 10) `
                -TimeoutSec 60
            $tripId = [long]$trip.id
        } else {
            $existingTrip = $existingTrips | Where-Object { $_.title -eq $testName }
            if ($null -eq $existingTrip) {
                throw "Reusable trip not found: $testName"
            }
            $tripId = [long]$existingTrip.id
        }
        $generation = Invoke-RestMethod `
            -Method Post `
            -Uri "$ApiBaseUrl/api/trips/$tripId/itinerary-generations" `
            -Headers $apiHeaders `
            -ContentType 'application/json' `
            -Body '{}' `
            -TimeoutSec 30
        $createdItems.Add([pscustomobject]@{
            name = $testName
            tripId = $tripId
            generationId = [long]$generation.generationId
            createdAt = (Get-Date).ToUniversalTime().ToString('o')
            initialStatus = $generation.status
        })
        Write-Step "Created generation for ${testName}: trip=$tripId, generation=$($generation.generationId)"
    }

    $requestsCreatedAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T4_TEN_REQUESTS_CREATED_BROKER_DOWN'; at = $requestsCreatedAt.ToUniversalTime().ToString('o') })
    $generationIds = [long[]]$createdItems.generationId

    # The sink failure cannot be observed before an event is actually published.
    # Check Debezium only after the 10 Outbox events exist.
    $debeziumDown = Wait-DebeziumStatus -Expected @('DOWN', 'UNREACHABLE') -TimeoutSeconds 60
    if ($null -ne $debeziumDown) {
        $timeline.Add([pscustomobject]@{ event = 'T3_DEBEZIUM_SINK_DOWN'; at = $debeziumDown.at.ToUniversalTime().ToString('o') })
    }
    Start-Sleep -Seconds 5

    $databaseDuring = Get-DatabaseSnapshot -GenerationIds $generationIds
    $offsetDuring = Get-OffsetSnapshot
    $slotDuring = Get-ReplicationSnapshot
    $rabbitUnreachableDuring = -not (Test-RabbitMqUp -Headers $rabbitHeaders)
    $debeziumDuringStatus = Get-DebeziumStatus
    $logsDuring = (Invoke-Docker -Arguments @(
        'logs', '--since', $tStop.ToUniversalTime().ToString('o'), 'planmate-debezium'
    ) -AllowFailure $true) | Out-String
    $logsDuring | Set-Content -LiteralPath (Join-Path $runDirectory 'debezium-broker-down.log') -Encoding utf8
    $sinkFailureCount = [regex]::Matches(
        $logsDuring,
        '(?i)(connection refused|connection reset|shutdown signal|already closed|rabbitmq|amqp)'
    ).Count

    Write-Step "During outage: outbox=$($databaseDuring.outboxCount), created=$($databaseDuring.createdCount), Debezium=$debeziumDuringStatus, sinkErrors=$sinkFailureCount"
    if ([long]$databaseDuring.outboxCount -ne $RequestCount) {
        throw "Expected $RequestCount Outbox events, got $($databaseDuring.outboxCount)"
    }
    if ([long]$databaseDuring.createdCount -ne $RequestCount) {
        throw "Expected $RequestCount CREATED generations, got $($databaseDuring.createdCount)"
    }

    Write-Step 'Starting RabbitMQ'
    $rabbitStartAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T5_RABBITMQ_START'; at = $rabbitStartAt.ToUniversalTime().ToString('o') })
    Invoke-Compose @('start', 'rabbitmq')

    $rabbitUpAt = Wait-RabbitMqUp -Headers $rabbitHeaders -Expected $true -TimeoutSeconds 90
    if ($null -eq $rabbitUpAt) {
        throw 'RabbitMQ did not become reachable within 90 seconds'
    }
    $rabbitMqWasStopped = $false
    $timeline.Add([pscustomobject]@{ event = 'T6_RABBITMQ_UP'; at = $rabbitUpAt.ToUniversalTime().ToString('o') })

    $prometheusRabbitUpAt = Wait-PrometheusUp -Job 'rabbitmq' -Expected 1 -TimeoutSeconds 60
    if ($null -ne $prometheusRabbitUpAt) {
        $timeline.Add([pscustomobject]@{ event = 'PROMETHEUS_RABBITMQ_UP'; at = $prometheusRabbitUpAt.ToUniversalTime().ToString('o') })
    }

    $debeziumRecovered = Wait-DebeziumStatus -Expected @('UP') -TimeoutSeconds 120
    if ($null -eq $debeziumRecovered) {
        Write-Step 'Debezium did not reconnect automatically; restarting connector container once'
        Invoke-Compose @('restart', 'debezium')
        $debeziumRecovered = Wait-DebeziumStatus -Expected @('UP') -TimeoutSeconds 120
    }
    if ($null -eq $debeziumRecovered) {
        throw 'Debezium did not recover after RabbitMQ restart'
    }
    $timeline.Add([pscustomobject]@{ event = 'T7_DEBEZIUM_SINK_UP'; at = $debeziumRecovered.at.ToUniversalTime().ToString('o') })

    $lastStatuses = @{}
    $terminalStates = @('READY_FOR_PLANNING', 'FAILED', 'COMPLETED')
    $recoveryDeadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    $allTerminal = $false
    do {
        $terminalCount = 0
        foreach ($item in $createdItems) {
            $current = Get-Generation -TripId $item.tripId -GenerationId $item.generationId -Headers $apiHeaders
            if ($lastStatuses[$item.generationId] -ne $current.status) {
                Write-Step "Generation $($item.generationId) -> $($current.status), candidates=$($current.candidateCount)"
                $lastStatuses[$item.generationId] = $current.status
            }
            if ($terminalStates -contains $current.status) {
                $terminalCount++
            }
        }
        $allTerminal = $terminalCount -eq $RequestCount
        if (-not $allTerminal) {
            Start-Sleep -Seconds 2
        }
    } while (-not $allTerminal -and (Get-Date) -lt $recoveryDeadline)

    $allTerminalAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T8_ALL_GENERATIONS_TERMINAL'; at = $allTerminalAt.ToUniversalTime().ToString('o') })

    $queueSettleDeadline = (Get-Date).AddSeconds(45)
    $queueSettled = $false
    do {
        $rabbitAfter = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
        $auditAfter = Get-RabbitQueueSnapshot -QueueName $auditQueueName -Headers $rabbitHeaders
        $queueSettled = [long]$rabbitAfter.ready -eq 0 `
            -and [long]$rabbitAfter.unacked -eq 0 `
            -and [long]$auditAfter.ready -eq $RequestCount
        if (-not $queueSettled) {
            Start-Sleep -Milliseconds 500
        }
    } while (-not $queueSettled -and (Get-Date) -lt $queueSettleDeadline)

    $queueSettledAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T9_QUEUE_SETTLED_AUDIT_TEN'; at = $queueSettledAt.ToUniversalTime().ToString('o') })
    $databaseAfter = Get-DatabaseSnapshot -GenerationIds $generationIds
    $dlqAfter = Get-RabbitQueueSnapshot -QueueName $dlqName -Headers $rabbitHeaders
    $offsetAfter = Get-OffsetSnapshot
    $slotAfter = Get-ReplicationSnapshot

    $auditMessages = Get-RabbitMessages -QueueName $auditQueueName -Count ($RequestCount + 5) -Headers $rabbitHeaders
    $auditMessages | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDirectory 'audit-messages.json') -Encoding utf8
    $auditGenerationIds = @(
        foreach ($message in $auditMessages) {
            try {
                $payload = $message.payload | ConvertFrom-Json
                [long]$payload.generationId
            } catch {
                Write-Step "Audit payload parse failed: $($_.Exception.Message)"
            }
        }
    )
    $uniqueAuditGenerationIds = @($auditGenerationIds | Sort-Object -Unique)
    $expectedGenerationIdText = (@($generationIds | Sort-Object) -join ',')
    $actualGenerationIdText = (@($uniqueAuditGenerationIds | Sort-Object) -join ',')

    $publishDelta = Get-RestartAwareDelta -After $rabbitAfter -Before $rabbitBefore -Property 'publish'
    $deliverDelta = Get-RestartAwareDelta -After $rabbitAfter -Before $rabbitBefore -Property 'deliver'
    $ackDelta = Get-RestartAwareDelta -After $rabbitAfter -Before $rabbitBefore -Property 'ack'
    $redeliverDelta = Get-RestartAwareDelta -After $rabbitAfter -Before $rabbitBefore -Property 'redeliver'
    $dlqDelta = [long]$dlqAfter.ready - [long]$dlqBefore.ready
    $lostEvents = $RequestCount - [long]$databaseAfter.readyCount
    $duplicateCandidateRows = [long]$databaseAfter.candidateCount - [long]$databaseAfter.distinctGenerationPlaceCount
    $duplicateRankRows = [long]$databaseAfter.candidateCount - [long]$databaseAfter.distinctGenerationRankCount
    $offsetHeldDuringOutage = $offsetBefore.sha256 -eq $offsetDuring.sha256
    $slotHeldDuringOutage = [string]$slotBefore.confirmedFlushLsn -eq [string]$slotDuring.confirmedFlushLsn
    $rabbitRecoverySeconds = ConvertTo-Seconds -End $rabbitUpAt -Start $rabbitStartAt
    $debeziumRecoveryAfterRabbitSeconds = ConvertTo-Seconds -End $debeziumRecovered.at -Start $rabbitUpAt
    $backlogRecoverySeconds = ConvertTo-Seconds -End $allTerminalAt -Start $rabbitStartAt
    $queueSettlementSeconds = ConvertTo-Seconds -End $queueSettledAt -Start $allTerminalAt
    $outageSeconds = ConvertTo-Seconds -End $rabbitStartAt -Start $tStop

    $finalLogs = (Invoke-Docker -Arguments @(
        'logs', '--since', $tStop.ToUniversalTime().ToString('o'), 'planmate-debezium'
    ) -AllowFailure $true) | Out-String
    $finalLogs | Set-Content -LiteralPath (Join-Path $runDirectory 'debezium-final.log') -Encoding utf8

    $checks = [ordered]@{
        rabbitMqBecameUnreachable = $rabbitUnreachableDuring
        prometheusObservedRabbitMqDown = $null -ne $prometheusRabbitDownAt
        debeziumSinkReportedDown = $null -ne $debeziumDown
        tenOutboxEventsStored = [long]$databaseDuring.outboxCount -eq $RequestCount
        tenGenerationsWaitedCreated = [long]$databaseDuring.createdCount -eq $RequestCount
        offsetFileHeldWhileBrokerDown = $offsetHeldDuringOutage
        replicationSlotHeldWhileBrokerDown = $slotHeldDuringOutage
        sinkFailureObservedInLog = $sinkFailureCount -gt 0
        rabbitMqRecovered = $null -ne $rabbitUpAt
        debeziumRecovered = $null -ne $debeziumRecovered
        auditQueueCapturedExactlyTen = [long]$auditAfter.ready -eq $RequestCount
        auditContainsExactlyExpectedGenerationIds = $expectedGenerationIdText -eq $actualGenerationIdText
        mainPublishedTen = $publishDelta -eq $RequestCount
        mainDeliveredTen = $deliverDelta -eq $RequestCount
        mainAckedTen = $ackDelta -eq $RequestCount
        allReady = [long]$databaseAfter.readyCount -eq $RequestCount
        noFailed = [long]$databaseAfter.failedCount -eq 0
        noCandidateDuplicates = $duplicateCandidateRows -eq 0 -and $duplicateRankRows -eq 0
        noDlqIncrease = $dlqDelta -eq 0
        noEventLoss = $lostEvents -eq 0
        mainQueueDrained = [long]$rabbitAfter.ready -eq 0 -and [long]$rabbitAfter.unacked -eq 0
    }
    $failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object Key)
    $verdict = if ($failedChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }

    $finishedAt = Get-Date
    $workerMetricText = Invoke-RestMethod -Uri 'http://localhost:8081/actuator/prometheus' -TimeoutSec 15
    $publicWorkerMetrics = @($workerMetricText -split "`n" | Where-Object {
        $_ -match '^# (HELP|TYPE) planmate_' -or $_ -match '^planmate_'
    }) -join "`n"

    $prometheusEvidence = [ordered]@{
        serviceHealth = Get-PrometheusRange `
            -Query 'up{job=~"planmate-backend|planmate-worker|rabbitmq|debezium"}' `
            -Start $runStartedAt.AddSeconds(-15) `
            -End $finishedAt.AddSeconds(15)
        generationStatus = Get-PrometheusRange `
            -Query 'planmate_itinerary_generation_status{status=~"CREATED|COLLECTING_CANDIDATES|READY_FOR_PLANNING|FAILED"}' `
            -Start $runStartedAt.AddSeconds(-15) `
            -End $finishedAt.AddSeconds(15)
        auditQueueReady = Get-PrometheusRange `
            -Query "rabbitmq_queue_messages_ready{queue=`"$auditQueueName`"}" `
            -Start $runStartedAt.AddSeconds(-15) `
            -End $finishedAt.AddSeconds(15)
        workerMetrics = $publicWorkerMetrics
    }
    $prometheusEvidence | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDirectory 'prometheus-evidence.json') -Encoding utf8

    $commitSha = ((git -C $repositoryRoot rev-parse HEAD) | Out-String).Trim()
    $result = [ordered]@{
        experimentId = $runId
        experimentName = 'RabbitMQ 중단 중 CDC 발행 실패와 복구'
        startedAt = $runStartedAt.ToUniversalTime().ToString('o')
        finishedAt = $finishedAt.ToUniversalTime().ToString('o')
        commitSha = $commitSha
        requestedTrips = $RequestCount
        testNames = @($createdItems.name)
        tripIds = @($createdItems.tripId)
        generationIds = @($generationIds)
        auditQueue = $auditQueueName
        outboxEventsCreated = [long]$databaseDuring.outboxCount
        createdWhileBrokerDown = [long]$databaseDuring.createdCount
        rabbitMqPublished = $publishDelta
        rabbitMqDelivered = $deliverDelta
        rabbitMqAcked = $ackDelta
        rabbitMqRedelivered = $redeliverDelta
        auditMessages = $auditMessages.Count
        auditDistinctGenerationIds = $uniqueAuditGenerationIds.Count
        readyForPlanning = [long]$databaseAfter.readyCount
        failed = [long]$databaseAfter.failedCount
        candidateRows = [long]$databaseAfter.candidateCount
        duplicateCandidateRows = $duplicateCandidateRows
        duplicateRankRows = $duplicateRankRows
        dlqBefore = [long]$dlqBefore.ready
        dlqAfter = [long]$dlqAfter.ready
        dlqDelta = $dlqDelta
        lostEvents = $lostEvents
        offsetBefore = $offsetBefore
        offsetDuring = $offsetDuring
        offsetAfter = $offsetAfter
        replicationSlotBefore = $slotBefore
        replicationSlotDuring = $slotDuring
        replicationSlotAfter = $slotAfter
        sinkFailureLogMatches = $sinkFailureCount
        debeziumDuringStatus = $debeziumDuringStatus
        rabbitMqOutageSeconds = $outageSeconds
        rabbitMqRecoverySeconds = $rabbitRecoverySeconds
        debeziumRecoveryAfterRabbitSeconds = $debeziumRecoveryAfterRabbitSeconds
        backlogRecoverySeconds = $backlogRecoverySeconds
        queueSettlementSeconds = $queueSettlementSeconds
        prometheusObservedRabbitMqDown = $null -ne $prometheusRabbitDownAt
        prometheusObservedRabbitMqUp = $null -ne $prometheusRabbitUpAt
        prometheusScrapeUncertaintySeconds = 15
        checks = $checks
        failedChecks = $failedChecks
        verdict = $verdict
    }

    $result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDirectory 'result.json') -Encoding utf8
    $createdItems | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runDirectory 'created-items.json') -Encoding utf8
    $timeline | Export-Csv -LiteralPath (Join-Path $runDirectory 'timeline.csv') -NoTypeInformation -Encoding utf8
    [ordered]@{
        mainBefore = $rabbitBefore
        mainAfter = $rabbitAfter
        dlqBefore = $dlqBefore
        dlqAfter = $dlqAfter
        auditBefore = $auditBefore
        auditAfter = $auditAfter
    } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'rabbitmq-snapshots.json') -Encoding utf8
    [ordered]@{
        before = $slotBefore
        during = $slotDuring
        after = $slotAfter
        offsetBefore = $offsetBefore
        offsetDuring = $offsetDuring
        offsetAfter = $offsetAfter
    } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'cdc-checkpoints.json') -Encoding utf8
    [ordered]@{
        during = $databaseDuring
        after = $databaseAfter
    } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'database-snapshots.json') -Encoding utf8

    $queries = @"
-- Experiment: $runId
-- Generation IDs: $($generationIds -join ', ')
SELECT id, trip_id, status, collection_claim_version, failure_reason
FROM itinerary_generations
WHERE id IN ($($generationIds -join ','))
ORDER BY id;

SELECT generation_id, count(*) AS candidates,
       count(DISTINCT place_id) AS distinct_places,
       count(DISTINCT rank) AS distinct_ranks
FROM place_candidates
WHERE generation_id IN ($($generationIds -join ','))
GROUP BY generation_id
ORDER BY generation_id;

SELECT slot_name, restart_lsn, confirmed_flush_lsn, active
FROM pg_replication_slots
WHERE slot_name = '$($settings['DEBEZIUM_SLOT_NAME'])';
"@
    $queries | Set-Content -LiteralPath (Join-Path $runDirectory 'queries.sql') -Encoding utf8

    $readme = @"
# RabbitMQ 중단 중 CDC 발행 실패와 복구 결과

- 실행 ID: ``$runId``
- 테스트 요청: ``장애테스트-래빗엠큐중단-01``~``10``
- 장애 시간: ${outageSeconds}초
- 최종 판정: **$verdict**

| 항목 | 실제값 |
| --- | ---: |
| 중단 중 Outbox | $($databaseDuring.outboxCount)건 |
| 중단 중 전달 대기 | $($databaseDuring.createdCount)건 |
| Audit Queue 이벤트 | $($auditAfter.ready)건 |
| RabbitMQ publish/deliver/ACK | $publishDelta / $deliverDelta / $ackDelta |
| READY | $($databaseAfter.readyCount)/$RequestCount |
| Candidate / 중복 | $($databaseAfter.candidateCount) / $duplicateCandidateRows |
| 이벤트 유실 | ${lostEvents}건 |
| DLQ 증가 | ${dlqDelta}건 |
| RabbitMQ Health 복구 | ${rabbitRecoverySeconds}초 |
| RabbitMQ 복구 후 Debezium UP | ${debeziumRecoveryAfterRabbitSeconds}초 |
| RabbitMQ Start 후 READY 10/10 | ${backlogRecoverySeconds}초 |

실패한 검사: $(if ($failedChecks.Count -eq 0) { '없음' } else { $failedChecks -join ', ' })
"@
    $readme | Set-Content -LiteralPath (Join-Path $runDirectory 'README.md') -Encoding utf8

    Write-Step "Experiment verdict: $verdict"
    Write-Step "Result: $(Join-Path $runDirectory 'result.json')"
    $result | ConvertTo-Json -Depth 20

    if ($verdict -ne 'PASS') {
        exit 2
    }
} finally {
    if ($rabbitMqWasStopped) {
        Write-Step 'Safety recovery: starting RabbitMQ in finally block'
        try {
            Invoke-Compose @('start', 'rabbitmq')
            if ($null -ne $rabbitHeaders) {
                Wait-RabbitMqUp -Headers $rabbitHeaders -Expected $true -TimeoutSeconds 90 | Out-Null
            }
        } catch {
            Write-Warning "RabbitMQ safety recovery failed: $($_.Exception.Message)"
        }
    }

    if ((Get-DebeziumStatus) -ne 'UP') {
        Write-Step 'Safety recovery: restarting Debezium'
        try {
            Invoke-Compose @('restart', 'debezium')
            Wait-DebeziumStatus -Expected @('UP') -TimeoutSeconds 120 | Out-Null
        } catch {
            Write-Warning "Debezium safety recovery failed: $($_.Exception.Message)"
        }
    }

    Stop-OwnedProcess -Process $workerProcess

    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }

    Protect-PublicEvidence
}
