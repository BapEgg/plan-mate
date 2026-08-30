# Worker 장기 정지와 Stale Generation 자동 복구 결과

- 실행 ID: `stale-generation-recovery-20260828-225751`
- 실험 시각: 2026-08-28 22:57:51 ~ 23:00:47 KST
- 테스트 요청: `장애테스트-스테일복구-01`~`10`, 총 10건
- Generation ID: `1196`~`1205`
- Processing Lease: 15초
- Worker A 결과 저장 지연: 120초
- Recovery Scan Interval: 10초
- 전용 Queue: `planmate.reliability.exp5.20260828225751.main`
- 전용 DLQ: `planmate.reliability.exp5.20260828225751.dlq`
- 최종 판정: **PASS**

## 검증 질문

> Worker A가 작업 소유권을 잡은 채 오래 멈추면 Lease 만료 후 Worker B가 자동으로 이어받고, 뒤늦게 돌아온 A의 오래된 결과가 B의 정상 결과를 덮어쓰지 못하게 차단하는가?

## 중학생도 이해할 수 있는 설명

- `Claim`은 “이 작업은 내가 처리하겠다”는 번호표다.
- `Lease`는 그 번호표를 믿어주는 제한 시간이다.
- `Stale`은 제한 시간이 지나 더 이상 믿을 수 없게 된 작업이다.
- `Recovery`는 다른 Worker가 새 번호표를 받아 작업을 이어가는 것이다.
- `Fencing`은 예전 번호표를 가진 Worker가 늦게 와서 최신 결과를 덮지 못하게 막는 것이다.

이번 실험에서 Worker A는 Version 1 번호표를 잡고 120초 동안 멈췄다. 15초 Lease가 끝난 뒤 Worker B가 Version 2를 받아 먼저 완료했다. 나중에 A가 결과를 저장하려 했지만 DB의 현재 번호는 2였기 때문에 Version 1 결과 10건은 모두 차단됐다.

## 장애 시뮬레이션

1. 테스트 요청 10건을 전용 실험 Queue에 넣었다.
2. Worker A를 Consumer 10개, Prefetch 1로 실행했다.
3. A가 10건 모두 Claim Version 1을 얻은 뒤 Candidate 결과 반환을 120초 지연했다.
4. DB는 `COLLECTING_CANDIDATES` 10건, RabbitMQ는 Unacked 10건인 상태가 됐다.
5. Processing Lease 15초가 지나 Stale 10건이 되는 것을 기다렸다.
6. Worker B와 Recovery Scheduler를 실행했다.
7. Scheduler가 10건을 다시 발행하고 B가 Claim Version 2로 처리했다.
8. B가 READY 10/10을 만든 뒤 늦게 돌아온 A의 Version 1 결과를 저장하도록 두었다.
9. Fencing 10건, Candidate 중복 0건, Queue 0건을 확인했다.

## 예상 문제와 복구 설계

Health Check만 보면 Worker A는 계속 UP이다. 그러나 실제로는 메시지 10건이 ACK되지 않고 DB 작업도 끝나지 않았다. 이런 상태를 무기한 기다리면 사용자는 결과를 받지 못한다.

반대로 다른 Worker가 무조건 재처리하면 A와 B가 동시에 결과를 저장해 중복 Candidate가 생기거나 늦은 A가 최신 B 결과를 덮을 수 있다.

따라서 다음 두 장치를 함께 사용한다.

1. Lease가 만료된 Generation만 Recovery Scheduler가 다시 발행한다.
2. Claim Version이 현재 DB Version과 일치하는 Worker만 결과를 저장한다.

## 실제 결과

| 검증 항목 | 기대값 | 실제값 | 판정 |
| --- | ---: | ---: | :---: |
| Worker A 초기 Claim | 10건 | 10건 | PASS |
| Worker A Claim Version | 1 | 1 | PASS |
| Worker A가 잡은 Queue Unacked | 10건 | 10건 | PASS |
| Lease 만료 | 10건 | 10건 | PASS |
| Recovery Publish | 10건 | 10건 | PASS |
| Worker B Recovery Claim | 10건 | 10건 | PASS |
| Worker B Claim Version | 2 | 2 | PASS |
| Worker B 성공 처리 | 10건 | 10건 | PASS |
| 늦은 Worker A 결과 Fencing | 10건 | 10건 | PASS |
| Worker A SKIP | 10건 | 10건 | PASS |
| 최종 READY / FAILED | 10 / 0건 | 10 / 0건 | PASS |
| Candidate / 중복 | 1,200 / 0행 | 1,200 / 0행 | PASS |
| RabbitMQ Publish / Deliver / ACK | 20 / 20 / 20회 | 20 / 20 / 20회 | PASS |
| Main Queue Ready / Unacked | 0 / 0건 | 0 / 0건 | PASS |
| 실험 DLQ | 0건 | 0건 | PASS |
| 운영 DLQ 변화 | 0건 | 167 → 167, 변화 0건 | PASS |

`Publish 20`은 최초 테스트 메시지 10건과 Scheduler의 복구 메시지 10건을 합한 값이다. A가 잡았던 최초 메시지 10건도 Fencing 후 정상 반환되어 최종 ACK됐으므로 Deliver와 ACK도 각각 20회다.

## 복구와 차단 시간

| 항목 | 실제 측정값 | 기준 |
| --- | ---: | --- |
| 모든 Lease 만료 관측 | **12.601초** | A의 Claim 10건 확인 → Stale 10건 관측 |
| Lease 만료 후 READY 10/10 | **13.688초** | Stale 10건 확인 → B 복구 완료 |
| Worker B 시작 후 READY 10/10 | **13.687초** | B Process Start → 복구 완료 |
| B 완료 후 A Fencing 10건 | **91.627초** | READY 10/10 → 늦은 A 결과 차단 완료 |
| Worker A 시작 후 Fencing 완료 | **132.913초** | A Process Start → Fencing 10건 |

15초 Lease는 각 Claim 시각부터 계산된다. 12.601초는 “모든 Claim이 이미 생성된 시각”부터 마지막 Lease 만료를 관측한 값이므로 Lease 설정값 15초와 모순되지 않는다.

## 내가 직접 화면을 보며 재현하는 방법

1. Docker Desktop에서 PostgreSQL, RabbitMQ, Debezium, Prometheus, Grafana가 실행 중인지 확인한다.
2. PowerShell에서 `./scripts/reliability-tests/Invoke-StaleGenerationRecovery.ps1`을 실행한다.
3. PlanMate에서 `장애테스트-스테일복구-01`~`10` 여행이 생성됐는지 확인한다.
4. RabbitMQ Management에서 이름이 `planmate.reliability.exp5.`로 시작하는 Main Queue를 연다.
5. Worker A가 작업을 잡은 시점에 Ready 0·Unacked 10인지 확인한다.
6. DBeaver에서 상태가 `COLLECTING_CANDIDATES`, Claim Version이 1인 10건을 확인한다.
7. Lease 만료 후 Worker B가 시작되면 Claim Version이 2로 바뀌고 READY 10건이 되는지 확인한다.
8. Worker A가 돌아온 뒤에도 Candidate가 1,200행에서 증가하지 않는지 확인한다.
9. Grafana의 `PlanMate 장애실험 05 · Stale Generation 복구와 Fencing`에서 ①~⑤ 패널을 확인한다.
10. `result.json`의 `verdict=PASS`와 빈 `failedChecks`를 확인한다.

```sql
SELECT id, trip_id, status, collection_claim_version,
       collection_lease_expires_at, failure_reason
FROM itinerary_generations
WHERE id BETWEEN 1196 AND 1205
ORDER BY id;

SELECT generation_id,
       count(*) AS candidate_count,
       count(DISTINCT place_id) AS distinct_places,
       count(DISTINCT rank) AS distinct_ranks
FROM place_candidates
WHERE generation_id BETWEEN 1196 AND 1205
GROUP BY generation_id
ORDER BY generation_id;
```

최종 기대값은 모든 Generation이 `READY_FOR_PLANNING`, Claim Version 2, Generation당 Candidate 120행이다.

## 실험 안전장치

- 실행 시각이 포함된 전용 Exchange·Queue·DLQ를 사용했다.
- 기존 운영 Main Queue는 실험 전후 Ready 0·Unacked 0을 확인했다.
- 기존 운영 DLQ 167건은 소비하거나 삭제하지 않았다.
- API Process는 Worker Listener와 Scheduler를 모두 비활성화했다.
- Worker A만 120초 지연을 사용했고 Worker B는 즉시 결과를 만들었다.
- Worker A는 Recovery Scheduler를 비활성화하고 Worker B만 Scheduler를 실행했다.
- Worker A/B는 각각 8081/8082로 분리해 Health와 Metric을 독립 관측했다.

## 교차검증 증거

| 증거 | Worker A | Worker B |
| --- | ---: | ---: |
| Claim 로그 | Version 1, 10줄 | Version 2, 10줄 |
| Recovery Publish 로그 | - | 10줄 |
| 처리 결과 Metric | SKIP 10 | 성공 10 |
| Fencing Metric | 10 | - |
| DB 최종 | Version 2·READY 10 | Candidate 1,200행 |

DB, Worker A/B 로그, 두 Worker의 Metric, RabbitMQ 스냅샷이 같은 수치를 가리킨다.

## Grafana 시각화 증거

Grafana Dashboard 원본: `infra/grafana/dashboards/planmate-reliability-experiment-05.json`

1. `images/01-장애범위-A정지-B복구.png`
2. `images/02-WorkerA-Claim1-Unacked10-Lease만료10.png`
3. `images/03-WorkerA-기대값-실제값.png`
4. `images/04-WorkerB-Recovery10-Claim2-성공10.png`
5. `images/05-WorkerB-기대값-실제값.png`
6. `images/06-Fencing10-SKIP10.png`
7. `images/07-최종판정-복구10-중복0.png`

핵심 결과는 `01 장애 범위 → 02 Worker A Stale → 04 Worker B 복구 → 06 Fencing → 07 최종 판정` 순서로 확인할 수 있다. 상세 수치는 03과 05의 기대값·실제값 표에서 확인한다.

## 구현하며 추가한 관측 기능

- 결정적 Candidate 공급자에 실험용 지연 Property 추가, 기본값은 0초
- Claim Metric을 `initial`과 `recovery`로 분리
- Scheduler Recovery Publish Metric 추가
- 오래된 Candidate 저장 차단 Fencing Metric 추가
- Claim 획득과 Fencing에 Generation ID·Trip ID·Claim Version 구조화 로그 추가
- Prometheus에서 Worker A와 Worker B를 별도 Job으로 수집

## 한계

- 로컬 Docker 환경에서 10건으로 수행한 복구 정확성 실험이며 부하 테스트가 아니다.
- 실제 네트워크 멈춤이나 JVM Stop-the-world 대신 Candidate 반환을 120초 지연해 “살아 있지만 진행하지 않는 Worker”를 결정적으로 재현했다.
- 운영 Lease는 15분이지만 실험 시간을 줄이기 위해 실험 Worker에서만 15초로 설정했다.
- Recovery Scheduler가 여러 인스턴스에서 동시에 실행되는 운영 구성은 별도 리더 선출이나 중복 Publish 관측이 필요하다.
- Prometheus 수집 간격이 15초이므로 정확한 복구 시간은 DB 폴링과 Process 시각을 사용했다.

## 원본 파일

| 파일 | 용도 |
| --- | --- |
| `result.json` | 모든 정량값과 PASS Check |
| `timeline.csv` | 장애·Lease 만료·복구·Fencing 시각 |
| `created-items.json` | 테스트명·Trip·Generation ID |
| `database-snapshots.json` | Created·Claim 1·Lease 만료·Claim 2·최종 DB 상태 |
| `rabbitmq-snapshots.json` | 운영/전용 Queue 전·중·후 원본값 |
| `worker-a.out.log` | 초기 Claim 10과 Fencing 10 로그 |
| `worker-b.out.log` | Recovery Publish 10과 Claim 2 로그 |
| `worker-a-final-metrics.prom` | Claim initial 10·Fenced 10·SKIP 10 |
| `worker-b-final-metrics.prom` | Recovery Publish 10·Claim recovery 10·Success 10 |
| `queries.sql` | DB 재검증 Query |
