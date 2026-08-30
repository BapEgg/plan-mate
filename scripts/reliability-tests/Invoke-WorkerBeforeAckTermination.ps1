#Requires -Version 7.0

[CmdletBinding()]
param(
    [int]$RequestCount = 10,
    [long]$UserId = 1,
    [string]$DestinationPlaceId = 'ChIJzWXFYYuifDUR64Pq5LTtioU',
    [string]$ApiBaseUrl = 'http://localhost:8080',
    [string]$WorkerBaseUrl = 'http://localhost:8081',
    [string]$PrometheusBaseUrl = 'http://localhost:9090',
    [string]$RabbitMqManagementBaseUrl = 'http://localhost:15672',
    [ValidateSet('AfterCommitBeforeAck', 'AfterDeliveryBeforeClaim')]
    [string]$FailurePoint = 'AfterCommitBeforeAck',
    [int]$AckPauseSeconds = 120,
    [int]$DeliveryBeforeClaimPauseSeconds = 120,
    [int]$RecoveryTimeoutSeconds = 300,
    [long[]]$ExistingTripIds = @(),
    [bool]$LeaveRecoveryWorkerRunning = $true
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repositoryRoot 'backend\.env'
$workerJar = Join-Path $repositoryRoot 'backend\build\libs\backend-0.0.1-SNAPSHOT.jar'
$runStartedAt = Get-Date
$isBeforeClaim = $FailurePoint -eq 'AfterDeliveryBeforeClaim'
$runPrefix = if ($isBeforeClaim) { 'worker-before-claim-termination' } else { 'worker-before-ack-termination' }
$testNamePrefix = if ($isBeforeClaim) { '장애테스트-Claim전Worker종료' } else { '장애테스트-워커ACK전중단' }
$experimentName = if ($isBeforeClaim) {
    'RabbitMQ 전달 후 DB Claim 이전 Worker 강제 종료'
} else {
    'DB 반영 후 ACK 이전 Worker 강제 종료'
}
$runId = '{0}-{1}' -f $runPrefix, $runStartedAt.ToString('yyyyMMdd-HHmmss')
$runDirectory = Join-Path $repositoryRoot "docs\reliability-tests\runs\$runId"
$transcriptPath = Join-Path $runDirectory 'commands.log'
$initialWorker = $null
$recoveryWorker = $null
$transcriptStarted = $false
$experimentSucceeded = $false

New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

function Write-Step([string]$Message) {
    Write-Host ('[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss.fff'), $Message)
}

function Protect-PublicEvidence {
    $extensions = @('.log', '.json', '.md', '.csv', '.sql', '.txt')
    $workspaceForwardSlash = $repositoryRoot.Replace('\', '/')
    $localUser = [Environment]::UserName
    $machineName = [Environment]::MachineName

    Get-ChildItem -LiteralPath $runDirectory -File | Where-Object {
        $extensions -contains $_.Extension.ToLowerInvariant()
    } | ForEach-Object {
        $text = Get-Content -Raw -LiteralPath $_.FullName
        if ($null -eq $text) {
            return
        }
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
        exp = $now + 1800
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
            $value = Get-PrometheusUp $Job
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

function Wait-HealthUp([string]$BaseUrl, [int]$TimeoutSeconds) {
    $baseUri = [Uri]$BaseUrl
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $tcpClient = [Net.Sockets.TcpClient]::new()
        try {
            $connectTask = $tcpClient.ConnectAsync($baseUri.Host, $baseUri.Port)
            if (-not $connectTask.Wait(500)) {
                Start-Sleep -Milliseconds 250
                continue
            }
            $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 3
            if ($health.status -eq 'UP') {
                return Get-Date
            }
        } catch {
        } finally {
            $tcpClient.Dispose()
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

function Get-Generation([long]$TripId, [long]$GenerationId, [hashtable]$Headers) {
    return Invoke-RestMethod `
        -Uri "$ApiBaseUrl/api/trips/$TripId/itinerary-generations/$GenerationId" `
        -Headers $Headers `
        -TimeoutSec 15
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
  'distinctGenerationPlaceCount', (SELECT count(DISTINCT (generation_id, place_id)) FROM place_candidates WHERE generation_id IN ($idList)),
  'distinctGenerationRankCount', (SELECT count(DISTINCT (generation_id, rank)) FROM place_candidates WHERE generation_id IN ($idList)),
  'minCandidatesPerGeneration', (SELECT COALESCE(min(candidate_count), 0) FROM (SELECT count(*) AS candidate_count FROM place_candidates WHERE generation_id IN ($idList) GROUP BY generation_id) c),
  'maxCandidatesPerGeneration', (SELECT COALESCE(max(candidate_count), 0) FROM (SELECT count(*) AS candidate_count FROM place_candidates WHERE generation_id IN ($idList) GROUP BY generation_id) c)
)::text;
"@
    return (Invoke-DatabaseScalar $sql | ConvertFrom-Json)
}

function Get-Delta($After, $Before, [string]$Property) {
    return [long]$After[$Property] - [long]$Before[$Property]
}

function ConvertTo-Seconds([datetime]$End, [datetime]$Start) {
    return [Math]::Round(($End - $Start).TotalSeconds, 3)
}

function Start-WorkerProcess(
    [int]$BeforeClaimPauseSeconds,
    [int]$BeforeAckPauseSeconds,
    [string]$LogName
) {
    $env:DB_URL = 'jdbc:postgresql://localhost:15432/planmate'
    $env:SPRING_DOCKER_COMPOSE_ENABLED = 'false'
    $env:SERVER_PORT = '8081'
    $env:APP_ITINERARY_MANUAL_HANDOFF_ENABLED = 'false'
    $env:APP_ITINERARY_GENERATION_WORKER_ENABLED = 'true'
    $env:APP_ITINERARY_GENERATION_WORKER_STALE_RECOVERY_ENABLED = 'false'
    $env:APP_ITINERARY_GENERATION_WORKER_RELIABILITY_AFTER_DELIVERY_BEFORE_CLAIM_DELAY = "${BeforeClaimPauseSeconds}s"
    $env:APP_ITINERARY_GENERATION_WORKER_RELIABILITY_AFTER_COMMIT_BEFORE_ACK_DELAY = "${BeforeAckPauseSeconds}s"
    $env:APP_ITINERARY_CANDIDATES_PROVIDER = 'deterministic'
    $env:SPRING_RABBITMQ_LISTENER_SIMPLE_PREFETCH = '1'

    $stdoutPath = Join-Path $runDirectory "$LogName.out.log"
    $stderrPath = Join-Path $runDirectory "$LogName.err.log"
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

    Write-Step "Run directory: $runDirectory"
    Write-Step 'Checking prerequisites'

    $apiHealth = Invoke-RestMethod -Uri "$ApiBaseUrl/actuator/health" -TimeoutSec 10
    if ($apiHealth.status -ne 'UP') {
        throw "API health is not UP: $($apiHealth.status)"
    }
    if ($null -ne (Wait-HealthUp -BaseUrl $WorkerBaseUrl -TimeoutSeconds 2)) {
        throw 'Port 8081 already has a healthy Worker. Stop it before this experiment.'
    }

    $accessToken = New-TestAccessToken -Secret $settings['JWT_SECRET'] -Subject $UserId
    $apiHeaders = @{ Authorization = "Bearer $accessToken" }
    $rabbitCredential = [Text.Encoding]::ASCII.GetBytes(
        $settings['RABBITMQ_USERNAME'] + ':' + $settings['RABBITMQ_PASSWORD']
    )
    $rabbitHeaders = @{ Authorization = 'Basic ' + [Convert]::ToBase64String($rabbitCredential) }

    $authStatus = Invoke-RestMethod -Uri "$ApiBaseUrl/api/auth/status" -Headers $apiHeaders -TimeoutSec 10
    if (-not $authStatus.authenticated -or [long]$authStatus.user.id -ne $UserId) {
        throw "Local test user authentication failed for user $UserId"
    }

    if ($ExistingTripIds.Count -gt 0 -and $ExistingTripIds.Count -ne $RequestCount) {
        throw "ExistingTripIds count must match RequestCount: ids=$($ExistingTripIds.Count), requests=$RequestCount"
    }
    if ($ExistingTripIds.Count -eq 0) {
        $existingCount = [long](Invoke-DatabaseScalar `
            "SELECT count(*) FROM trips WHERE title LIKE '$testNamePrefix-%';")
        if ($existingCount -ne 0) {
            throw "Existing $FailurePoint test trips found: $existingCount. Use ExistingTripIds to reuse them."
        }
    } else {
        $tripIdList = $ExistingTripIds -join ','
        $ownedTripCount = [long](Invoke-DatabaseScalar `
            "SELECT count(*) FROM trips WHERE id IN ($tripIdList) AND created_by = $UserId;")
        if ($ownedTripCount -ne $RequestCount) {
            throw "Expected $RequestCount reusable trips owned by user $UserId, got $ownedTripCount"
        }
    }

    $mainQueueName = 'planmate.itinerary.generation.requested'
    $dlqName = 'planmate.itinerary.generation.requested.dlq'
    $rabbitBeforeWorker = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
    if ($rabbitBeforeWorker.ready -ne 0 -or $rabbitBeforeWorker.unacked -ne 0) {
        throw "Main queue is not empty before the experiment: ready=$($rabbitBeforeWorker.ready), unacked=$($rabbitBeforeWorker.unacked)"
    }
    if ($rabbitBeforeWorker.consumers -ne 0) {
        throw "Expected no Worker consumer before starting the isolated Worker, got $($rabbitBeforeWorker.consumers)"
    }

    $timeline = [System.Collections.Generic.List[object]]::new()
    $timeline.Add([pscustomobject]@{ event = 'T0_API_READY_NO_WORKER'; at = (Get-Date).ToUniversalTime().ToString('o') })

    $initialBeforeClaimPause = if ($isBeforeClaim) { $DeliveryBeforeClaimPauseSeconds } else { 0 }
    $initialBeforeAckPause = if ($isBeforeClaim) { 0 } else { $AckPauseSeconds }
    Write-Step ("Starting isolated Worker at failurePoint=$FailurePoint, " +
        "beforeClaimPause=${initialBeforeClaimPause}s, beforeAckPause=${initialBeforeAckPause}s, prefetch=1")
    $initialWorkerStartedAt = Get-Date
    $initialWorker = Start-WorkerProcess `
        -BeforeClaimPauseSeconds $initialBeforeClaimPause `
        -BeforeAckPauseSeconds $initialBeforeAckPause `
        -LogName 'worker-before-kill'
    $initialWorkerHealthAt = Wait-HealthUp -BaseUrl $WorkerBaseUrl -TimeoutSeconds 90
    if ($null -eq $initialWorkerHealthAt) {
        throw 'Initial Worker did not become healthy within 90 seconds'
    }
    $timeline.Add([pscustomobject]@{ event = 'T1_INITIAL_WORKER_UP'; at = $initialWorkerHealthAt.ToUniversalTime().ToString('o') })

    $consumerDeadline = (Get-Date).AddSeconds(30)
    do {
        $rabbitBefore = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
        if ($rabbitBefore.consumers -eq 1) { break }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $consumerDeadline)
    if ($rabbitBefore.consumers -ne 1) {
        throw "Expected exactly one isolated Worker consumer, got $($rabbitBefore.consumers)"
    }
    $dlqBefore = Get-RabbitQueueSnapshot -QueueName $dlqName -Headers $rabbitHeaders

    $createdItems = [System.Collections.Generic.List[object]]::new()
    for ($index = 1; $index -le $RequestCount; $index++) {
        $testName = '{0}-{1:D2}' -f $testNamePrefix, $index
        if ($ExistingTripIds.Count -eq 0) {
            $tripPayload = New-TripPayload -Title $testName -FreeRequest "$experimentName run $runId"
            $trip = Invoke-RestMethod `
                -Method Post `
                -Uri "$ApiBaseUrl/api/trips" `
                -Headers $apiHeaders `
                -ContentType 'application/json' `
                -Body ($tripPayload | ConvertTo-Json -Depth 10) `
                -TimeoutSec 60
            $tripId = [long]$trip.id
        } else {
            $tripId = [long]$ExistingTripIds[$index - 1]
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
        Write-Step "Created ${testName}: trip=$tripId, generation=$($generation.generationId)"
    }
    $requestsCreatedAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T2_TEN_REQUESTS_CREATED'; at = $requestsCreatedAt.ToUniversalTime().ToString('o') })
    $generationIds = [long[]]$createdItems.generationId

    $hookLogPath = Join-Path $runDirectory 'worker-before-kill.out.log'
    $hookPattern = if ($isBeforeClaim) {
        'RELIABILITY_HOOK_AFTER_DELIVERY_BEFORE_CLAIM'
    } else {
        'RELIABILITY_HOOK_AFTER_COMMIT_BEFORE_ACK'
    }
    $hookDeadline = (Get-Date).AddSeconds(120)
    $hookObserved = $false
    do {
        $databaseAtHook = Get-DatabaseSnapshot -GenerationIds $generationIds
        $rabbitAtHook = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
        $hookObserved = (Test-Path -LiteralPath $hookLogPath) -and
            [bool](Select-String -LiteralPath $hookLogPath `
                -Pattern $hookPattern -Quiet)
        $databaseStateReached = if ($isBeforeClaim) {
            [long]$databaseAtHook.createdCount -eq $RequestCount `
                -and [long]$databaseAtHook.collectingCount -eq 0 `
                -and [long]$databaseAtHook.readyCount -eq 0 `
                -and [long]$databaseAtHook.candidateCount -eq 0
        } else {
            [long]$databaseAtHook.readyCount -eq 1
        }
        if ($hookObserved -and $databaseStateReached -and $rabbitAtHook.unacked -eq 1) {
            break
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $hookDeadline)

    if (-not $hookObserved) {
        throw 'Reliability hook was not observed before timeout'
    }
    if (-not $databaseStateReached -or [long]$rabbitAtHook.unacked -ne 1) {
        throw ("Unexpected state at ${FailurePoint}: created=$($databaseAtHook.createdCount), " +
                "collecting=$($databaseAtHook.collectingCount), ready=$($databaseAtHook.readyCount), " +
                "candidates=$($databaseAtHook.candidateCount), unacked=$($rabbitAtHook.unacked)")
    }

    $hookLine = Select-String -LiteralPath $hookLogPath -Pattern $hookPattern | Select-Object -Last 1
    if ($hookLine.Line -notmatch 'generationId=(\d+)') {
        throw "Could not read generationId from reliability hook log: $($hookLine.Line)"
    }
    $killedGenerationId = [long]$Matches[1]
    $hookObservedAt = Get-Date
    $hookEvent = if ($isBeforeClaim) { 'T3_AFTER_DELIVERY_BEFORE_CLAIM' } else { 'T3_AFTER_COMMIT_BEFORE_ACK' }
    $timeline.Add([pscustomobject]@{ event = $hookEvent; at = $hookObservedAt.ToUniversalTime().ToString('o') })
    Write-Step ("Hook observed: generation=$killedGenerationId, created=$($databaseAtHook.createdCount), " +
        "collecting=$($databaseAtHook.collectingCount), ready=$($databaseAtHook.readyCount), " +
        "candidates=$($databaseAtHook.candidateCount), queueReady=$($rabbitAtHook.ready), unacked=1")

    $initialWorkerMetricText = Invoke-RestMethod -Uri "$WorkerBaseUrl/actuator/prometheus" -TimeoutSec 15
    $publicInitialWorkerMetrics = @($initialWorkerMetricText -split "`n" | Where-Object {
        $_ -match '^# (HELP|TYPE) planmate_' -or $_ -match '^planmate_'
    }) -join "`n"
    $publicInitialWorkerMetrics | Set-Content `
        -LiteralPath (Join-Path $runDirectory 'worker-before-kill-metrics.txt') `
        -Encoding utf8
    $deliveryBeforeKillMetricObserved =
        $initialWorkerMetricText -match 'planmate_itinerary_generation_worker_delivery_total\{redelivered="false"\}\s+1\.0'
    $claimBeforeKillMetricObserved =
        $initialWorkerMetricText -match 'planmate_itinerary_generation_worker_claim_total'
    $beforeClaimHookMetricObserved =
        $initialWorkerMetricText -match 'planmate_itinerary_generation_worker_reliability_hook_total\{phase="after_delivery_before_claim"\}\s+1\.0'

    Write-Step "Force stopping isolated Worker PID $($initialWorker.Id) at $FailurePoint"
    $workerKilledAt = Get-Date
    Stop-OwnedProcess $initialWorker
    $timeline.Add([pscustomobject]@{ event = 'T4_WORKER_FORCE_KILLED'; at = $workerKilledAt.ToUniversalTime().ToString('o') })

    $requeueDeadline = (Get-Date).AddSeconds(30)
    do {
        $rabbitAfterKill = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
        if ($rabbitAfterKill.unacked -eq 0 -and $rabbitAfterKill.ready -eq $RequestCount) { break }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $requeueDeadline)
    $requeueObservedAt = Get-Date
    if ($rabbitAfterKill.unacked -ne 0 -or $rabbitAfterKill.ready -ne $RequestCount) {
        throw "Expected requeue ready=$RequestCount and unacked=0, got ready=$($rabbitAfterKill.ready), unacked=$($rabbitAfterKill.unacked)"
    }
    $timeline.Add([pscustomobject]@{ event = 'T5_UNACKED_REQUEUED'; at = $requeueObservedAt.ToUniversalTime().ToString('o') })
    Write-Step "RabbitMQ requeued unacked message: ready=$($rabbitAfterKill.ready), unacked=0"

    $prometheusDownAt = Wait-PrometheusUp -Job 'planmate-worker' -Expected 0 -TimeoutSeconds 45
    if ($null -eq $prometheusDownAt) {
        throw 'Prometheus did not observe isolated Worker DOWN within 45 seconds'
    }
    $timeline.Add([pscustomobject]@{ event = 'PROMETHEUS_WORKER_DOWN'; at = $prometheusDownAt.ToUniversalTime().ToString('o') })

    Write-Step 'Starting recovery Worker without reliability pauses'
    $recoveryWorkerStartedAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T6_RECOVERY_WORKER_START'; at = $recoveryWorkerStartedAt.ToUniversalTime().ToString('o') })
    $recoveryWorker = Start-WorkerProcess `
        -BeforeClaimPauseSeconds 0 `
        -BeforeAckPauseSeconds 0 `
        -LogName 'worker-after-restart'
    $recoveryWorkerHealthAt = Wait-HealthUp -BaseUrl $WorkerBaseUrl -TimeoutSeconds 90
    if ($null -eq $recoveryWorkerHealthAt) {
        throw 'Recovery Worker did not become healthy within 90 seconds'
    }
    $timeline.Add([pscustomobject]@{ event = 'T7_RECOVERY_WORKER_UP'; at = $recoveryWorkerHealthAt.ToUniversalTime().ToString('o') })

    $prometheusUpAt = Wait-PrometheusUp -Job 'planmate-worker' -Expected 1 -TimeoutSeconds 45
    if ($null -eq $prometheusUpAt) {
        throw 'Prometheus did not observe recovery Worker UP within 45 seconds'
    }
    $timeline.Add([pscustomobject]@{ event = 'PROMETHEUS_WORKER_UP'; at = $prometheusUpAt.ToUniversalTime().ToString('o') })

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
            Start-Sleep -Seconds 1
        }
    } while (-not $allTerminal -and (Get-Date) -lt $recoveryDeadline)

    $allTerminalAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T8_ALL_GENERATIONS_TERMINAL'; at = $allTerminalAt.ToUniversalTime().ToString('o') })

    $queueSettleDeadline = (Get-Date).AddSeconds(30)
    do {
        $rabbitAfter = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
        if ($rabbitAfter.ready -eq 0 -and $rabbitAfter.unacked -eq 0) { break }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $queueSettleDeadline)
    $queueSettledAt = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T9_QUEUE_SETTLED'; at = $queueSettledAt.ToUniversalTime().ToString('o') })

    $databaseAfter = Get-DatabaseSnapshot -GenerationIds $generationIds
    $dlqAfter = Get-RabbitQueueSnapshot -QueueName $dlqName -Headers $rabbitHeaders
    $killedGenerationCandidates = [long](Invoke-DatabaseScalar `
        "SELECT count(*) FROM place_candidates WHERE generation_id = $killedGenerationId;")

    $recoveryLogPath = Join-Path $runDirectory 'worker-after-restart.out.log'
    $redeliveredLogObserved = (Test-Path -LiteralPath $recoveryLogPath) -and
        [bool](Select-String -LiteralPath $recoveryLogPath `
            -Pattern "generationId=$killedGenerationId, tripId=.*redelivered=true" -Quiet)
    $recoveryMetricText = Invoke-RestMethod -Uri "$WorkerBaseUrl/actuator/prometheus" -TimeoutSec 15
    $publicRecoveryMetrics = @($recoveryMetricText -split "`n" | Where-Object {
        $_ -match '^# (HELP|TYPE) planmate_' -or $_ -match '^planmate_'
    }) -join "`n"
    $publicRecoveryMetrics | Set-Content `
        -LiteralPath (Join-Path $runDirectory 'worker-after-restart-metrics.txt') `
        -Encoding utf8
    $skippedMetricObserved = $recoveryMetricText -match 'planmate_itinerary_generation_worker_processed_total\{result="skipped"\}\s+1\.0'
    $redeliveredMetricObserved = $recoveryMetricText -match 'planmate_itinerary_generation_worker_delivery_total\{redelivered="true"\}\s+1\.0'
    $initialClaimMetricCount = 0
    if ($recoveryMetricText -match 'planmate_itinerary_generation_worker_claim_total\{type="initial"\}\s+([0-9.]+)') {
        $initialClaimMetricCount = [double]$Matches[1]
    }

    $publishDelta = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'publish'
    $deliverDelta = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'deliver'
    $ackDelta = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'ack'
    $redeliverDelta = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'redeliver'
    $dlqDelta = [long]$dlqAfter.ready - [long]$dlqBefore.ready
    $lostEvents = $RequestCount - [long]$databaseAfter.readyCount
    $duplicateCandidateRows = [long]$databaseAfter.candidateCount - [long]$databaseAfter.distinctGenerationPlaceCount

    $checks = [ordered]@{
        deliveryMetricRecordedBeforeKill = $deliveryBeforeKillMetricObserved
        beforeClaimHookMetricRecorded = if ($isBeforeClaim) { $beforeClaimHookMetricObserved } else { $true }
        noClaimBeforeKill = if ($isBeforeClaim) { -not $claimBeforeKillMetricObserved } else { $true }
        allCreatedBeforeClaimKill = if ($isBeforeClaim) {
            [long]$databaseAtHook.createdCount -eq $RequestCount `
                -and [long]$databaseAtHook.collectingCount -eq 0 `
                -and [long]$databaseAtHook.readyCount -eq 0 `
                -and [long]$databaseAtHook.candidateCount -eq 0
        } else { $true }
        readyCountMatchesFailurePoint = if ($isBeforeClaim) {
            [long]$databaseAtHook.readyCount -eq 0
        } else {
            [long]$databaseAtHook.readyCount -eq 1
        }
        exactlyOneUnackedBeforeKill = [long]$rabbitAtHook.unacked -eq 1
        workerWasForceKilled = $initialWorker.HasExited
        unackedWasRequeued = [long]$rabbitAfterKill.ready -eq $RequestCount -and [long]$rabbitAfterKill.unacked -eq 0
        redeliveryObserved = $redeliverDelta -eq 1 -and $redeliveredLogObserved -and $redeliveredMetricObserved
        recoveryOutcomeMatchesFailurePoint = if ($isBeforeClaim) {
            -not $skippedMetricObserved -and $initialClaimMetricCount -eq $RequestCount
        } else {
            $skippedMetricObserved
        }
        publishedTen = $publishDelta -eq $RequestCount
        deliveredInitialPlusRecovery = $deliverDelta -eq ($RequestCount + 1)
        ackedTen = $ackDelta -eq $RequestCount
        allReady = [long]$databaseAfter.readyCount -eq $RequestCount
        noFailed = [long]$databaseAfter.failedCount -eq 0
        noDlqIncrease = $dlqDelta -eq 0
        noEventLoss = $lostEvents -eq 0
        noDuplicateCandidates = $duplicateCandidateRows -eq 0 -and [long]$databaseAfter.candidateCount -eq ($RequestCount * 120)
        killedGenerationHasOneCandidateSet = $killedGenerationCandidates -eq 120
        mainQueueDrained = [long]$rabbitAfter.ready -eq 0 -and [long]$rabbitAfter.unacked -eq 0
    }
    $failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object Key)
    $verdict = if ($failedChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }

    $commitSha = (& git -C $repositoryRoot rev-parse HEAD).Trim()
    $result = [ordered]@{
        experimentId = $runId
        experimentName = $experimentName
        failurePoint = $FailurePoint
        startedAt = $runStartedAt.ToUniversalTime().ToString('o')
        finishedAt = (Get-Date).ToUniversalTime().ToString('o')
        commitSha = $commitSha
        requestedTrips = $RequestCount
        reusedExistingTrips = $ExistingTripIds.Count -gt 0
        reusedTripIds = @($ExistingTripIds)
        testNames = @($createdItems.name)
        tripIds = @($createdItems.tripId)
        generationIds = @($generationIds)
        killedGenerationId = $killedGenerationId
        initialWorkerPid = $initialWorker.Id
        recoveryWorkerPid = $recoveryWorker.Id
        ackMode = 'AUTO'
        prefetch = 1
        ackPauseSeconds = $AckPauseSeconds
        deliveryBeforeClaimPauseSeconds = $DeliveryBeforeClaimPauseSeconds
        createdBeforeKill = [long]$databaseAtHook.createdCount
        collectingBeforeKill = [long]$databaseAtHook.collectingCount
        readyBeforeKill = [long]$databaseAtHook.readyCount
        candidatesBeforeKill = [long]$databaseAtHook.candidateCount
        queueReadyBeforeKill = [long]$rabbitAtHook.ready
        queueUnackedBeforeKill = [long]$rabbitAtHook.unacked
        queueReadyAfterKill = [long]$rabbitAfterKill.ready
        queueUnackedAfterKill = [long]$rabbitAfterKill.unacked
        rabbitMqPublishedDelta = $publishDelta
        rabbitMqDeliveredDelta = $deliverDelta
        rabbitMqAckedDelta = $ackDelta
        rabbitMqRedeliveredDelta = $redeliverDelta
        deliveryBeforeKillMetricObserved = $deliveryBeforeKillMetricObserved
        beforeClaimHookMetricObserved = $beforeClaimHookMetricObserved
        claimBeforeKillMetricObserved = $claimBeforeKillMetricObserved
        redeliveredLogObserved = $redeliveredLogObserved
        redeliveredMetricObserved = $redeliveredMetricObserved
        skippedMetricObserved = $skippedMetricObserved
        initialClaimMetricCount = $initialClaimMetricCount
        readyForPlanning = [long]$databaseAfter.readyCount
        failed = [long]$databaseAfter.failedCount
        candidateRows = [long]$databaseAfter.candidateCount
        distinctGenerationPlaceRows = [long]$databaseAfter.distinctGenerationPlaceCount
        distinctGenerationRankRows = [long]$databaseAfter.distinctGenerationRankCount
        duplicateCandidateRows = $duplicateCandidateRows
        killedGenerationCandidateRows = $killedGenerationCandidates
        dlqBefore = [long]$dlqBefore.ready
        dlqAfter = [long]$dlqAfter.ready
        dlqDelta = $dlqDelta
        lostEvents = $lostEvents
        requeueSeconds = ConvertTo-Seconds -End $requeueObservedAt -Start $workerKilledAt
        workerHealthRecoverySeconds = ConvertTo-Seconds -End $recoveryWorkerHealthAt -Start $recoveryWorkerStartedAt
        backlogRecoverySeconds = ConvertTo-Seconds -End $allTerminalAt -Start $recoveryWorkerStartedAt
        queueSettlementSeconds = ConvertTo-Seconds -End $queueSettledAt -Start $allTerminalAt
        prometheusWorkerDownAt = $prometheusDownAt.ToUniversalTime().ToString('o')
        prometheusWorkerUpAt = $prometheusUpAt.ToUniversalTime().ToString('o')
        prometheusScrapeUncertaintySeconds = 15
        checks = $checks
        failedChecks = $failedChecks
        verdict = $verdict
    }

    $result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'result.json') -Encoding utf8
    $createdItems | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runDirectory 'created-items.json') -Encoding utf8
    $timeline | Export-Csv -LiteralPath (Join-Path $runDirectory 'timeline.csv') -NoTypeInformation -Encoding utf8
    [ordered]@{
        beforeWorker = $rabbitBeforeWorker
        beforeRequests = $rabbitBefore
        atFailurePoint = $rabbitAtHook
        afterKillRequeue = $rabbitAfterKill
        afterRecovery = $rabbitAfter
        dlqBefore = $dlqBefore
        dlqAfter = $dlqAfter
    } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'rabbitmq-snapshots.json') -Encoding utf8

    $queries = @"
-- Experiment: $runId
-- Generation IDs: $($generationIds -join ', ')
SELECT id, trip_id, status, collection_claim_version, created_at, updated_at
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

    $failureDescription = if ($isBeforeClaim) {
        'RabbitMQ가 1건을 Worker A에 전달했지만, Worker A가 DB 선점(claim)을 시작하기 전에 강제 종료됐다.'
    } else {
        'Worker A가 DB 반영을 끝냈지만 RabbitMQ ACK를 반환하기 전에 강제 종료됐다.'
    }
    $recoveryDescription = if ($isBeforeClaim) {
        "재기동 Worker B가 재전달 1건을 포함한 10건을 모두 최초 선점(initial claim)으로 처리했다. SKIP은 발생하지 않았다."
    } else {
        '재기동 Worker B가 이미 처리된 재전달 1건을 SKIP하고 나머지 요청을 처리했다.'
    }
    $summary = @"
# $experimentName 결과

- 실행 ID: $runId
- 장애 지점: $FailurePoint
- 테스트 요청: ${RequestCount}건
- 기존 완료 여행 재사용: $(if ($ExistingTripIds.Count -gt 0) { '예 (외부 장소 조회를 실험 범위에서 제외)' } else { '아니오' })
- ACK 모드: AUTO
- Prefetch: 1
- 강제 종료 대상 Generation: $killedGenerationId
- 판정: **$verdict**

## 장애 상황

$failureDescription

## 복구 해석

$recoveryDescription

## 핵심 결과

| 항목 | 결과 |
| --- | ---: |
| 종료 직전 RabbitMQ 전달/unacked | 1/1건 |
| 종료 직전 DB CREATED | $($databaseAtHook.createdCount)건 |
| 종료 직전 DB 선점(claim) | $(if ($claimBeforeKillMetricObserved) { '관측됨' } else { '0건' }) |
| 종료 직전 DB 후보 수집 완료 | $($databaseAtHook.readyCount)건 |
| 종료 직전 Candidate Row | $($databaseAtHook.candidateCount)건 |
| 종료 직전 Queue unacked | $($rabbitAtHook.unacked)건 |
| 종료 후 Queue ready/unacked | $($rabbitAfterKill.ready)/$($rabbitAfterKill.unacked) |
| RabbitMQ publish/deliver/ack | $publishDelta/$deliverDelta/$ackDelta |
| RabbitMQ redelivery 증가 | $redeliverDelta |
| 재기동 Worker SKIP | $skippedMetricObserved |
| 재기동 Worker 최초 선점 | $initialClaimMetricCount 건 |
| 최종 후보 수집 완료 | $($databaseAfter.readyCount)/$RequestCount |
| 중복 Candidate Row | $duplicateCandidateRows |
| 이벤트 유실 | ${lostEvents}건 |
| DLQ 증가 | ${dlqDelta}건 |
| 재큐잉 시간 | $(ConvertTo-Seconds -End $requeueObservedAt -Start $workerKilledAt)초 |
| 재기동 후 전체 처리 시간 | $(ConvertTo-Seconds -End $allTerminalAt -Start $recoveryWorkerStartedAt)초 |

실패한 검사: $(if ($failedChecks.Count -eq 0) { '없음' } else { $failedChecks -join ', ' })
"@
    $summary | Set-Content -LiteralPath (Join-Path $runDirectory 'README.md') -Encoding utf8

    $experimentSucceeded = $verdict -eq 'PASS'
    Write-Step "Experiment verdict: $verdict"
    Write-Step "Result: $(Join-Path $runDirectory 'result.json')"
    $result | ConvertTo-Json -Depth 10
} finally {
    Stop-OwnedProcess $initialWorker
    if (-not $experimentSucceeded -or -not $LeaveRecoveryWorkerRunning) {
        Stop-OwnedProcess $recoveryWorker
    } elseif ($null -ne $recoveryWorker -and -not $recoveryWorker.HasExited) {
        $recoveryWorker.Id | Set-Content -LiteralPath (Join-Path $runDirectory 'recovery-worker.pid') -Encoding ascii
        Write-Step "Recovery Worker left running on port 8081 with PID $($recoveryWorker.Id)"
    }
    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }
    Protect-PublicEvidence -Directory $runDirectory
}
