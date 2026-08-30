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
$runId = 'debezium-stop-restart-{0}' -f $runStartedAt.ToString('yyyyMMdd-HHmmss')
$runDirectory = Join-Path $repositoryRoot "docs\reliability-tests\runs\$runId"
$transcriptPath = Join-Path $runDirectory 'commands.log'
$debeziumWasStopped = $false
$transcriptStarted = $false

New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

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

function Invoke-DatabaseScalar([string]$Sql) {
    $output = & docker compose --env-file $envFile -f $composeFile exec -T postgres `
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

function Test-DebeziumUp {
    try {
        $health = Invoke-RestMethod -Uri 'http://localhost:8083/q/health' -TimeoutSec 3
        return $health.status -eq 'UP'
    } catch {
        return $false
    }
}

function Wait-DebeziumUp([int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (Test-DebeziumUp) {
            return Get-Date
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
    $sql = @"
SELECT json_build_object(
  'outboxCount', (SELECT count(*) FROM outbox_events WHERE aggregate_id IN ($($GenerationIds | ForEach-Object { "'$_'" } | Join-String -Separator ','))),
  'createdCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'CREATED'),
  'collectingCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'COLLECTING_CANDIDATES'),
  'readyCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'READY_FOR_PLANNING'),
  'failedCount', (SELECT count(*) FROM itinerary_generations WHERE id IN ($idList) AND status = 'FAILED'),
  'candidateCount', (SELECT count(*) FROM place_candidates WHERE generation_id IN ($idList))
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

$settings = Read-DotEnv $envFile
foreach ($requiredSetting in @('JWT_SECRET', 'RABBITMQ_USERNAME', 'RABBITMQ_PASSWORD', 'POSTGRES_USER', 'POSTGRES_DB')) {
    if (-not $settings.ContainsKey($requiredSetting) -or [string]::IsNullOrWhiteSpace($settings[$requiredSetting])) {
        throw "Required setting is missing: $requiredSetting"
    }
}

try {
    Start-Transcript -LiteralPath $transcriptPath | Out-Null
    $transcriptStarted = $true

    Write-Step "Run directory: $runDirectory"
    Write-Step 'Checking prerequisites'

    $backendHealth = Invoke-RestMethod -Uri "$ApiBaseUrl/actuator/health" -TimeoutSec 10
    if ($backendHealth.status -ne 'UP') {
        throw "Backend health is not UP: $($backendHealth.status)"
    }
    if (-not (Test-DebeziumUp)) {
        throw 'Debezium must be UP before the experiment'
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

    $existingCount = [long](Invoke-DatabaseScalar `
        "SELECT count(*) FROM trips WHERE title LIKE '장애테스트-데비지움중단-%';")
    if ($existingCount -ne 0) {
        throw "Existing Debezium test trips found: $existingCount. Use a clean title set before rerunning."
    }

    $mainQueueName = 'planmate.itinerary.generation.requested'
    $dlqName = 'planmate.itinerary.generation.requested.dlq'
    $rabbitBefore = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
    $dlqBefore = Get-RabbitQueueSnapshot -QueueName $dlqName -Headers $rabbitHeaders
    if ($rabbitBefore.ready -ne 0 -or $rabbitBefore.unacked -ne 0) {
        throw "Main queue is not empty before the experiment: ready=$($rabbitBefore.ready), unacked=$($rabbitBefore.unacked)"
    }
    if ($rabbitBefore.consumers -lt 1) {
        throw 'No itinerary generation Worker consumer is connected'
    }

    $timeline = [System.Collections.Generic.List[object]]::new()
    $timeline.Add([pscustomobject]@{ event = 'T0_BASELINE_READY'; at = (Get-Date).ToUniversalTime().ToString('o') })

    Write-Step 'Stopping Debezium'
    $t1 = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T1_DEBEZIUM_STOP'; at = $t1.ToUniversalTime().ToString('o') })
    Invoke-Compose @('stop', 'debezium')
    $debeziumWasStopped = $true

    $downDeadline = (Get-Date).AddSeconds(30)
    while ((Test-DebeziumUp) -and (Get-Date) -lt $downDeadline) {
        Start-Sleep -Milliseconds 500
    }
    if (Test-DebeziumUp) {
        throw 'Debezium health endpoint did not go DOWN'
    }
    $tDownObserved = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'DEBEZIUM_HEALTH_DOWN'; at = $tDownObserved.ToUniversalTime().ToString('o') })
    Write-Step 'Debezium health is DOWN'

    $prometheusDownAt = Wait-PrometheusUp -Job 'debezium' -Expected 0 -TimeoutSeconds 45
    if ($null -ne $prometheusDownAt) {
        $timeline.Add([pscustomobject]@{ event = 'PROMETHEUS_DEBEZIUM_DOWN'; at = $prometheusDownAt.ToUniversalTime().ToString('o') })
        Write-Step 'Prometheus observed Debezium DOWN'
    } else {
        Write-Step 'Prometheus did not observe Debezium DOWN within 45 seconds'
    }

    $createdItems = [System.Collections.Generic.List[object]]::new()
    for ($index = 1; $index -le $RequestCount; $index++) {
        $testName = '장애테스트-데비지움중단-{0:D2}' -f $index
        $tripPayload = New-TripPayload -Title $testName -FreeRequest "Debezium stop/restart run $runId"
        $trip = Invoke-RestMethod `
            -Method Post `
            -Uri "$ApiBaseUrl/api/trips" `
            -Headers $apiHeaders `
            -ContentType 'application/json' `
            -Body ($tripPayload | ConvertTo-Json -Depth 10) `
            -TimeoutSec 60
        $generation = Invoke-RestMethod `
            -Method Post `
            -Uri "$ApiBaseUrl/api/trips/$($trip.id)/itinerary-generations" `
            -Headers $apiHeaders `
            -ContentType 'application/json' `
            -Body '{}' `
            -TimeoutSec 30
        $createdItems.Add([pscustomobject]@{
            name = $testName
            tripId = [long]$trip.id
            generationId = [long]$generation.generationId
            createdAt = (Get-Date).ToUniversalTime().ToString('o')
            initialStatus = $generation.status
        })
        Write-Step "Created ${testName}: trip=$($trip.id), generation=$($generation.generationId), status=$($generation.status)"
    }

    $t3 = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T3_TEN_REQUESTS_CREATED'; at = $t3.ToUniversalTime().ToString('o') })
    $generationIds = [long[]]$createdItems.generationId

    Start-Sleep -Seconds 3
    $databaseDuring = Get-DatabaseSnapshot -GenerationIds $generationIds
    $rabbitDuring = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
    $dlqDuring = Get-RabbitQueueSnapshot -QueueName $dlqName -Headers $rabbitHeaders
    Write-Step "During outage: outbox=$($databaseDuring.outboxCount), created=$($databaseDuring.createdCount), queueReady=$($rabbitDuring.ready)"

    if ([long]$databaseDuring.outboxCount -ne $RequestCount) {
        throw "Expected $RequestCount Outbox events during outage, got $($databaseDuring.outboxCount)"
    }
    if ([long]$databaseDuring.createdCount -ne $RequestCount) {
        throw "Expected $RequestCount CREATED generations during outage, got $($databaseDuring.createdCount)"
    }

    Write-Step 'Starting Debezium'
    $t4 = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T4_DEBEZIUM_START'; at = $t4.ToUniversalTime().ToString('o') })
    Invoke-Compose @('start', 'debezium')

    $t5 = Wait-DebeziumUp -TimeoutSeconds 90
    if ($null -eq $t5) {
        throw 'Debezium did not become UP within 90 seconds'
    }
    $debeziumWasStopped = $false
    $timeline.Add([pscustomobject]@{ event = 'T5_DEBEZIUM_HEALTH_UP'; at = $t5.ToUniversalTime().ToString('o') })
    Write-Step "Debezium health recovered in $(ConvertTo-Seconds -End $t5 -Start $t4) seconds"

    $prometheusUpAt = Wait-PrometheusUp -Job 'debezium' -Expected 1 -TimeoutSeconds 45
    if ($null -ne $prometheusUpAt) {
        $timeline.Add([pscustomobject]@{ event = 'PROMETHEUS_DEBEZIUM_UP'; at = $prometheusUpAt.ToUniversalTime().ToString('o') })
        Write-Step 'Prometheus observed Debezium UP'
    } else {
        Write-Step 'Prometheus did not observe Debezium UP within 45 seconds'
    }

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

    $t7 = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T7_GENERATION_OBSERVATION_FINISHED'; at = $t7.ToUniversalTime().ToString('o') })

    # READY is committed before the listener returns and RabbitMQ receives its ACK.
    # Wait for that expected short gap so the final snapshot does not create a false failure.
    $queueSettleDeadline = (Get-Date).AddSeconds(30)
    $queueSettled = $false
    do {
        $rabbitAfter = Get-RabbitQueueSnapshot -QueueName $mainQueueName -Headers $rabbitHeaders
        $publishDeltaAtSettle = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'publish'
        $deliverDeltaAtSettle = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'deliver'
        $ackDeltaAtSettle = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'ack'
        $queueSettled = [long]$rabbitAfter.ready -eq 0 `
            -and [long]$rabbitAfter.unacked -eq 0 `
            -and $publishDeltaAtSettle -ge $RequestCount `
            -and $deliverDeltaAtSettle -ge $RequestCount `
            -and $ackDeltaAtSettle -ge $RequestCount
        if (-not $queueSettled) {
            Start-Sleep -Milliseconds 500
        }
    } while (-not $queueSettled -and (Get-Date) -lt $queueSettleDeadline)

    $t8 = Get-Date
    $timeline.Add([pscustomobject]@{ event = 'T8_QUEUE_SETTLEMENT_OBSERVED'; at = $t8.ToUniversalTime().ToString('o') })
    $databaseAfter = Get-DatabaseSnapshot -GenerationIds $generationIds
    $dlqAfter = Get-RabbitQueueSnapshot -QueueName $dlqName -Headers $rabbitHeaders

    $publishDelta = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'publish'
    $deliverDelta = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'deliver'
    $ackDelta = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'ack'
    $redeliverDelta = Get-Delta -After $rabbitAfter -Before $rabbitBefore -Property 'redeliver'
    $dlqDelta = [long]$dlqAfter.ready - [long]$dlqBefore.ready
    $lostEvents = $RequestCount - [long]$databaseAfter.readyCount
    $connectorRecoverySeconds = ConvertTo-Seconds -End $t5 -Start $t4
    $backlogRecoverySeconds = ConvertTo-Seconds -End $t7 -Start $t4
    $queueSettlementSeconds = ConvertTo-Seconds -End $t8 -Start $t7

    $checks = [ordered]@{
        outboxEventsCreated = [long]$databaseDuring.outboxCount -eq $RequestCount
        noPublishWhileStopped = (Get-Delta -After $rabbitDuring -Before $rabbitBefore -Property 'publish') -eq 0
        publishedAfterRestart = $publishDelta -eq $RequestCount
        deliveredAfterRestart = $deliverDelta -eq $RequestCount
        ackedAfterRestart = $ackDelta -eq $RequestCount
        allReady = [long]$databaseAfter.readyCount -eq $RequestCount
        noFailed = [long]$databaseAfter.failedCount -eq 0
        noDlqIncrease = $dlqDelta -eq 0
        noEventLoss = $lostEvents -eq 0
        mainQueueDrained = [long]$rabbitAfter.ready -eq 0 -and [long]$rabbitAfter.unacked -eq 0
    }
    $failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object Key)
    $verdict = if ($failedChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }

    $commitSha = (& git -C $repositoryRoot rev-parse HEAD).Trim()
    $result = [ordered]@{
        experimentId = $runId
        experimentName = 'Debezium 중단 후 재시작'
        startedAt = $runStartedAt.ToUniversalTime().ToString('o')
        finishedAt = (Get-Date).ToUniversalTime().ToString('o')
        commitSha = $commitSha
        requestedTrips = $RequestCount
        testNames = @($createdItems.name)
        tripIds = @($createdItems.tripId)
        generationIds = @($generationIds)
        outboxEventsCreated = [long]$databaseDuring.outboxCount
        rabbitMqPublishedDelta = $publishDelta
        rabbitMqDeliveredDelta = $deliverDelta
        rabbitMqAckedDelta = $ackDelta
        rabbitMqRedeliveredDelta = $redeliverDelta
        readyForPlanning = [long]$databaseAfter.readyCount
        failed = [long]$databaseAfter.failedCount
        dlqBefore = [long]$dlqBefore.ready
        dlqAfter = [long]$dlqAfter.ready
        dlqDelta = $dlqDelta
        lostEvents = $lostEvents
        candidateRows = [long]$databaseAfter.candidateCount
        connectorRecoverySeconds = $connectorRecoverySeconds
        backlogRecoverySeconds = $backlogRecoverySeconds
        queueSettlementSeconds = $queueSettlementSeconds
        prometheusObservedDown = $null -ne $prometheusDownAt
        prometheusObservedUp = $null -ne $prometheusUpAt
        prometheusScrapeUncertaintySeconds = 15
        checks = $checks
        failedChecks = $failedChecks
        verdict = $verdict
    }

    $result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'result.json') -Encoding utf8
    $createdItems | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runDirectory 'created-items.json') -Encoding utf8
    $timeline | Export-Csv -LiteralPath (Join-Path $runDirectory 'timeline.csv') -NoTypeInformation -Encoding utf8
    [ordered]@{
        before = $rabbitBefore
        during = $rabbitDuring
        after = $rabbitAfter
        dlqBefore = $dlqBefore
        dlqDuring = $dlqDuring
        dlqAfter = $dlqAfter
    } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'rabbitmq-snapshots.json') -Encoding utf8

    $summary = @"
# Debezium 중단 후 재시작 결과

- 실행 ID: $runId
- Git 커밋: $commitSha
- 테스트 요청: ${RequestCount}건
- 판정: **$verdict**

## 핵심 결과

| 항목 | 결과 |
| --- | ---: |
| 중단 중 생성된 Outbox 이벤트 | $($databaseDuring.outboxCount)건 |
| RabbitMQ publish 증가량 | $publishDelta |
| RabbitMQ deliver 증가량 | $deliverDelta |
| RabbitMQ ack 증가량 | $ackDelta |
| READY_FOR_PLANNING | $($databaseAfter.readyCount)/$RequestCount |
| FAILED | $($databaseAfter.failedCount)건 |
| DLQ 증가량 | $dlqDelta |
| 이벤트 유실 | ${lostEvents}건 |
| Connector 복구 시간 | ${connectorRecoverySeconds}초 |
| 전체 적체 처리 시간 | ${backlogRecoverySeconds}초 |
| 마지막 상태 전환 후 Queue 안정화 | ${queueSettlementSeconds}초 |

## 판정 근거

실패한 검사: $(if ($failedChecks.Count -eq 0) { '없음' } else { $failedChecks -join ', ' })

Grafana/Prometheus는 15초 수집 간격의 시각적 증거로 사용하고, 정확한 Connector 복구 시간은 Debezium health endpoint 폴링 결과를 사용한다.
"@
    $summary | Set-Content -LiteralPath (Join-Path $runDirectory 'README.md') -Encoding utf8

    Write-Step "Experiment verdict: $verdict"
    Write-Step "Result: $(Join-Path $runDirectory 'result.json')"
    $result | ConvertTo-Json -Depth 10

    if ($verdict -ne 'PASS') {
        exit 2
    }
} finally {
    if ($debeziumWasStopped) {
        Write-Step 'Safety recovery: starting Debezium in finally block'
        try {
            Invoke-Compose @('start', 'debezium')
            $recoveredAt = Wait-DebeziumUp -TimeoutSeconds 90
            if ($null -eq $recoveredAt) {
                Write-Warning 'Debezium did not recover during safety cleanup'
            }
        } catch {
            Write-Warning "Safety recovery failed: $($_.Exception.Message)"
        }
    }
    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }
}
