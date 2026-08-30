# 장애 주입 테스트 02 — DB 반영 후 ACK 이전 Worker 강제 종료

## 1. 최종 판정

**PASS**

DB 후보 저장과 `READY_FOR_PLANNING` 반영을 마쳤지만 RabbitMQ ACK를 보내기 전인 Worker를 강제로 종료했다. 미확인 메시지 1건은 Queue로 돌아갔고, 재시작 Worker가 이를 `redelivered=true`로 다시 받았다. 이미 완료된 Generation임을 확인해 중복 처리를 `SKIP`했으며, 최종 10/10건 완료·후보 중복 0건·이벤트 유실 0건을 확인했다.

| 항목 | 기록 |
| --- | --- |
| 실행 ID | `worker-before-ack-termination-20260828-211457` |
| 실험 시각 | 2026-08-28 21:14:57 ~ 21:15:46 KST |
| 대상 커밋 | `49312494b486e91e9b1ef0e55730cd366117fb88` |
| 요청 수 | 10건 |
| Generation ID | `1166` ~ `1175` |
| 강제 종료한 최초 Worker PID | `10896` |
| 복구 Worker PID | `31592` |
| RabbitMQ ACK 모드 | `AUTO` |
| Prefetch | `1` |
| ACK 전 실험 대기 | 120초 |

## 2. 검증 질문과 가설

### 검증 질문

> Worker가 DB 작업을 Commit했지만 RabbitMQ ACK를 보내기 전에 죽으면 메시지가 유실되거나, 재전달된 메시지가 후보를 중복 저장하지 않는가?

### 가설

1. RabbitMQ는 ACK되지 않은 메시지를 Worker 연결 종료 후 Queue로 되돌린다.
2. 복구 Worker는 해당 메시지를 `redelivered=true`로 받는다.
3. DB 상태가 이미 `READY_FOR_PLANNING`이면 작업 Claim을 다시 얻지 못하고 `SKIP`한다.
4. 나머지 9건은 정상 처리되어 최종 10/10건이 완료된다.
5. Candidate 중복, DLQ 증가, 이벤트 유실은 모두 0건이어야 한다.

## 3. 장애 주입과 복구 설계

### 장애 주입

- API와 Worker를 별도 Process로 실행했다.
- Worker는 한 번에 메시지 하나만 가져가도록 `prefetch=1`로 고정했다.
- 첫 메시지의 DB Commit 이후 Listener 반환 직전에 Reliability Hook으로 120초 대기했다.
- DB `READY 1건`과 RabbitMQ `Unacked 1건`을 동시에 확인한 뒤 해당 Worker PID만 강제 종료했다.

### 예상 문제

- ACK가 없으면 RabbitMQ가 같은 메시지를 다시 전달하므로 **at-least-once 전달**이 발생한다.
- Consumer가 단순히 작업을 다시 수행하면 같은 Generation에 Candidate가 두 번 저장될 수 있다.
- 재전달 메시지를 잘못 ACK하거나 버리면 이벤트 유실 또는 DLQ 증가가 발생할 수 있다.

### 복구 방안

- Worker 연결 종료 시 RabbitMQ의 자동 Requeue를 사용한다.
- Generation Claim이 이미 완료된 상태라면 작업을 다시 수행하지 않고 `SKIP`한다.
- `redelivered` 전달 Metric과 `processed{result="skipped"}` Metric을 분리해 재전달과 멱등 처리를 관측한다.
- Queue가 `ready=0, unacked=0`으로 안정화될 때까지 판정을 미룬다.

## 4. 실험 통제 조건

이번 실험의 목적은 외부 장소 API가 아니라 RabbitMQ ACK·재전달·멱등 처리 검증이다. 따라서 실험 Worker에서만 `app.itinerary.candidates.provider=deterministic`을 사용해 Generation마다 동일한 120개 후보를 만들었다. 운영 기본값은 `google`이며 바뀌지 않는다.

실행 당시 Google Places API가 `PLACE_PROVIDER_UNAVAILABLE`을 반환했기 때문에 여행 생성 단계는 1차 실험에서 만들어 둔 사용자 소유 여행 10건(ID `1245`~`1254`)을 재사용했다. 각 여행에는 새로운 Generation을 API로 생성했으며, 테스트 구분명은 `장애테스트-워커ACK전중단-01`~`10`으로 기록했다.

이 통제는 메시징 복구라는 독립 변수를 선명하게 만들지만, Google Places 연동까지 포함한 End-to-End 성공을 의미하지는 않는다.

## 5. 내가 직접 실행하는 수동 테스트 절차

Docker Container로 API와 Worker를 분리했다면 `planmate-worker`만 Stop/Start하고, 현재 로컬 구성처럼 Java Process로 분리했다면 Worker Process만 종료한다.

1. API, Debezium, RabbitMQ, PostgreSQL이 정상이고 Main Queue가 `Ready 0 / Unacked 0`인지 확인한다.
2. API는 Worker 비활성으로 실행하고, 별도 Worker는 `prefetch=1`, ACK 전 대기 120초 설정으로 실행한다.
3. PlanMate 화면에서 `장애테스트-워커ACK전중단-01`~`10` 여행 또는 기존 여행에 대한 Generation 10건을 만든다.
4. DBeaver에서 대상 Generation 중 `READY_FOR_PLANNING`이 정확히 1건인지 확인한다.
5. RabbitMQ Management의 `planmate.itinerary.generation.requested` Queue에서 `Ready 9 / Unacked 1`을 확인한다.
6. ACK 대기 중인 `planmate-worker` Container의 Stop 버튼을 누르거나 Worker Process를 강제 종료한다.
7. RabbitMQ 화면에서 `Ready 10 / Unacked 0`으로 바뀌는지 확인한다.
8. ACK 대기 설정을 0초로 바꿔 Worker를 다시 Start한다.
9. Grafana에서 Worker `DOWN → UP`, Queue `Ready 10 → 0`, 재전달 1건, SKIP 1건을 확인한다.
10. DBeaver에서 10/10건이 `READY_FOR_PLANNING`, Generation당 Candidate 120건, Candidate 중복 0건인지 확인한다.
11. RabbitMQ Main Queue `Ready 0 / Unacked 0`과 DLQ 증가 0건을 확인하고 증거 이미지를 저장한다.

이번 Run은 DBeaver에서 아래 SQL로 그대로 재검증할 수 있다.

```sql
SELECT id, trip_id, status, collection_claim_version
FROM itinerary_generations
WHERE id BETWEEN 1166 AND 1175
ORDER BY id;

SELECT generation_id,
       count(*) AS candidate_count,
       count(DISTINCT place_id) AS distinct_places,
       count(DISTINCT rank) AS distinct_ranks
FROM place_candidates
WHERE generation_id BETWEEN 1166 AND 1175
GROUP BY generation_id
ORDER BY generation_id;
```

첫 번째 결과는 10행 모두 `READY_FOR_PLANNING`, 두 번째 결과는 각 행의 세 숫자가 모두 `120`이면 중복 없이 통과한 것이다.

정확한 ACK 직전 종료는 사람 손으로 타이밍을 맞추기 어렵다. 포트폴리오 재현용 측정은 아래 자동 실행기를 사용하는 편이 안전하다.

```powershell
.\scripts\reliability-tests\Invoke-WorkerBeforeAckTermination.ps1 `
  -RequestCount 10 `
  -ExistingTripIds 1245,1246,1247,1248,1249,1250,1251,1252,1253,1254 `
  -AckPauseSeconds 120 `
  -RecoveryTimeoutSeconds 300
```

## 6. 실제 관측 타임라인

| 시각(KST) | 사건 |
| --- | --- |
| 21:15:02.390 | API 정상·Worker 없음 기준선 기록 |
| 21:15:15.293 | 최초 Worker Health UP |
| 21:15:16.758 | Generation 10건 생성 완료 |
| 21:15:21.892 | DB 첫 건 READY·Candidate 120, RabbitMQ Ready 9·Unacked 1 |
| 21:15:21.896 | 최초 Worker PID 10896 강제 종료 |
| 21:15:25.959 | RabbitMQ Ready 10·Unacked 0 재큐잉 확인 |
| 21:15:25.983 | 복구 Worker 시작 |
| 21:15:39.408 | 복구 Worker Health UP |
| 21:15:43.432 | Prometheus Worker UP 관측 |
| 21:15:43.600 | 10/10건 READY, 재전달 1·SKIP 1 확인 |
| 21:15:45.990 | Queue Ready 0·Unacked 0 안정화 |

## 7. 기대값과 실제값

| 검증 항목 | 기대값 | 실제값 | 판정 |
| --- | ---: | ---: | :---: |
| 종료 직전 DB 완료 | 1건 | 1건 | PASS |
| 종료 직전 Queue Unacked | 1건 | 1건 | PASS |
| 종료 후 Queue Ready/Unacked | 10/0 | 10/0 | PASS |
| RabbitMQ Publish 증가 | 10회 | 10회 | PASS |
| RabbitMQ Deliver 증가 | 최초 1 + 복구 10 = 11회 | 11회 | PASS |
| RabbitMQ ACK 증가 | 메시지 10건 = 10회 | 10회 | PASS |
| RabbitMQ Redelivery 증가 | 1회 | 1회 | PASS |
| `redelivered=true` 로그·Metric | 관측 | 관측 | PASS |
| 중복 처리 차단 `SKIP` | 1건 | 1건 | PASS |
| 후보 수집 완료 | 10/10건 | 10/10건 | PASS |
| Candidate 저장 | 1,200행 | 1,200행 | PASS |
| Generation+Place 중복 | 0행 | 0행 | PASS |
| Generation+Rank 중복 | 0행 | 0행 | PASS |
| 처리 실패 | 0건 | 0건 | PASS |
| DLQ 증가 | 0건 | 0건 | PASS |
| 이벤트 유실 | 0건 | 0건 | PASS |
| Main Queue 최종 | Ready 0·Unacked 0 | Ready 0·Unacked 0 | PASS |

`Deliver 11`과 `ACK 10`은 모순이 아니다. 최초 Worker가 1건을 전달받고 ACK 전에 죽었기 때문에 같은 메시지를 포함해 복구 Worker가 10건을 다시 전달받았다. 최종 메시지는 10건이므로 ACK는 10회다.

## 8. 복구 시간

| 측정 항목 | 결과 | 기준 |
| --- | ---: | --- |
| Unacked 메시지 재큐잉 | **4.063초** | Worker 강제 종료 → Ready 10 확인 |
| Worker Health 복구 | **13.425초** | 복구 Worker Start → Health UP |
| Backlog 10건 처리 완료 | **17.617초** | 복구 Worker Start → 10/10 READY |
| Queue 최종 안정화 | **2.390초** | 10/10 READY → Ready 0·Unacked 0 재확인 |

Prometheus 수집 간격은 15초이므로 Grafana의 Worker DOWN/UP 경계에는 최대 약 15초의 관측 오차가 있다. 정확한 Health 복구 시간은 0.5초 간격 Health endpoint 폴링값이다. Unacked 1건은 약 4초 동안만 존재해 Prometheus Queue 시계열에는 잡히지 않았고 RabbitMQ Management API 스냅샷으로 증명했다.

## 9. 포트폴리오 이미지

| 이미지 | 설명 |
| --- | --- |
| `images/worker-before-ack-portfolio-overview.png` | 실험 2 전용 Grafana 전체 요약 |
| `images/worker-before-ack-01-worker-health.png` | API·RabbitMQ 정상, Worker만 DOWN→UP인 장애 범위와 전체 타임라인 |
| `images/worker-before-ack-02-queue-recovery.png` | Worker 종료 후 Ready 10, 복구 후 0인 Queue 흐름 |
| `images/worker-before-ack-03-verdict.png` | 기대값·실제값과 PASS 판정표 |
| `images/worker-before-ack-04-redelivery-skip.png` | 복구 Worker 원본 9·재전달 1·SKIP 1 Metric |
| `images/worker-before-ack-05-recovery-explanation.png` | 재큐잉·재전달·멱등 처리의 5단계 해설 |
| `images/worker-before-ack-04-result-summary.svg` | 포트폴리오 카드용 정량 요약 |

Grafana Dashboard 원본은 `infra/grafana/dashboards/planmate-reliability-experiment-02.json`이다.

## 10. 배운 점과 설계 변경

1. `AUTO ACK`를 명시하고 Listener가 반환된 뒤에만 ACK되는 구간을 로그로 확인했다.
2. 메시지 수신 Metric을 `redelivered=true|false`로 나눠 재전달을 직접 관측할 수 있게 했다.
3. ACK 직전 종료 타이밍을 재현하기 위해 기본값 0초인 Reliability Hook을 추가했다. 설정하지 않으면 운영 동작은 바뀌지 않는다.
4. API와 Worker를 분리해 Worker 장애가 API 장애로 확대되지 않도록 실험 환경을 구성했다.
5. 외부 장소 API를 실험 변수에서 제거하는 결정적 Candidate 공급자를 조건부로 추가했다.
6. 완료 상태 Claim이 거절되면 재전달 작업을 `SKIP`하는 기존 멱등 로직이 후보 중복을 막는 것을 실제 검증했다.

## 11. 한계

- 로컬 Docker Desktop에서 10건을 사용한 복구 정확성 실험이며 부하 테스트가 아니다.
- 최초 Worker의 UP 구간은 Prometheus 15초 수집 간격보다 짧아 Grafana에 샘플이 남지 않았다. 최초 Worker Health UP은 자동 실행기의 endpoint 폴링 시각으로 기록했다.
- 기존 DLQ에는 과거 테스트 데이터 167건이 있었지만, 이 실험 전후 증가는 0건이었다. 따라서 판정은 절대값이 아니라 Delta를 사용했다.
- 여행 데이터는 1차 실험의 10건을 재사용했고 Candidate 수집은 결정적 실험 공급자를 사용했다.
- Worker Process 강제 종료를 검증했으며 호스트 전원 장애, 네트워크 Partition, RabbitMQ Cluster 장애는 범위 밖이다.

## 12. 원본 증거

| 파일 | 용도 |
| --- | --- |
| `result.json` | 실제 측정값과 모든 PASS Check |
| `timeline.csv` | 장애 전·중·후 UTC 시각 |
| `created-items.json` | 테스트명, Trip ID, Generation ID |
| `rabbitmq-snapshots.json` | Queue 전·중·후 Management API 원본값 |
| `worker-before-kill.out.log` | ACK 직전 Hook과 최초 처리 로그 |
| `worker-after-restart.out.log` | `redelivered=true`, `SKIP`, 복구 처리 로그 |
| `queries.sql` | DB 재검증 SQL |

실패한 검사는 없다. 상세 기계 판정은 `result.json`의 `checks`와 `failedChecks`를 기준으로 한다.
