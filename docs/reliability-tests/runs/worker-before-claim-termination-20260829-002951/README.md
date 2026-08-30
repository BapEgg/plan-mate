# RabbitMQ 전달 후 DB Claim 이전 Worker 강제 종료 결과

- 실행 ID: worker-before-claim-termination-20260829-002951
- 장애 지점: AfterDeliveryBeforeClaim
- 테스트 요청: 10건
- 기존 완료 여행 재사용: 예 (외부 장소 조회를 실험 범위에서 제외)
- ACK 모드: AUTO
- Prefetch: 1
- 강제 종료 대상 Generation: 1226
- 판정: **PASS**

## 장애 상황

RabbitMQ가 1건을 Worker A에 전달했지만, Worker A가 DB 선점(claim)을 시작하기 전에 강제 종료됐다.

## 복구 해석

재기동 Worker B가 재전달 1건을 포함한 10건을 모두 최초 선점(initial claim)으로 처리했다. SKIP은 발생하지 않았다.

## 핵심 결과

| 항목 | 결과 |
| --- | ---: |
| 종료 직전 RabbitMQ 전달/unacked | 1/1건 |
| 종료 직전 DB CREATED | 10건 |
| 종료 직전 DB 선점(claim) | 0건 |
| 종료 직전 DB 후보 수집 완료 | 0건 |
| 종료 직전 Candidate Row | 0건 |
| 종료 직전 Queue unacked | 1건 |
| 종료 후 Queue ready/unacked | 10/0 |
| RabbitMQ publish/deliver/ack | 10/11/10 |
| RabbitMQ redelivery 증가 | 1 |
| 재기동 Worker SKIP | False |
| 재기동 Worker 최초 선점 | 10 건 |
| 최종 후보 수집 완료 | 10/10 |
| 중복 Candidate Row | 0 |
| 이벤트 유실 | 0건 |
| DLQ 증가 | 0건 |
| 재큐잉 시간 | 4.563초 |
| 재기동 후 전체 처리 시간 | 14.074초 |

실패한 검사: 없음

## 중학생도 이해할 수 있는 실험 설명

RabbitMQ를 우체국, Worker를 배달 기사라고 생각하면 된다. 우체국은 기사 A에게 편지 1통을 건넸지만, 기사 A는 일을 시작했다는 DB 기록을 남기기 전에 꺼졌다. 기사 A가 완료 도장(ACK)을 찍지 않았기 때문에 우체국은 그 편지를 버리지 않고 대기함으로 돌려놓았다. 기사 B가 켜지자 같은 편지를 다시 받아 나머지 9통과 함께 총 10통을 처리했다.

이 실험에서 중요한 것은 단순히 Worker가 꺼졌다는 사실이 아니다. 종료 직전에 아래 세 조건이 동시에 성립해야 정확히 **DB 선점 전 장애**라고 말할 수 있다.

1. RabbitMQ 전달은 1건이다.
2. Queue `unacked`는 1건이다.
3. DB claim, 후보, 완료는 모두 0건이다.

## 직접 눈으로 재현하는 순서

### 가장 쉬운 방법 — 자동 주입을 실행하면서 화면 관측

1. Docker Desktop에서 PostgreSQL, RabbitMQ, Debezium, Prometheus, Grafana가 실행 중인지 확인한다.
2. API 서버는 8080에서 켜고 Worker 포트 8081은 비워둔다.
3. Grafana의 `PlanMate 장애실험 07 · 전달 후 DB 선점 전 Worker 종료` 대시보드를 연다.
4. PowerShell에서 아래 명령을 실행한다.

```powershell
./scripts/reliability-tests/Invoke-WorkerBeforeAckTermination.ps1 `
  -FailurePoint AfterDeliveryBeforeClaim `
  -RequestCount 10 `
  -DeliveryBeforeClaimPauseSeconds 120 `
  -AckPauseSeconds 0 `
  -ExistingTripIds 1323,1324,1325,1326,1327,1328,1329,1330,1331,1332 `
  -LeaveRecoveryWorkerRunning $false
```

5. RabbitMQ Management의 `planmate.itinerary.generation.requested` Queue에서 종료 직전 `ready=9`, `unacked=1`을 확인한다.
6. DBeaver에서 대상 Generation 10건이 모두 `CREATED`이고 `place_candidates`가 0행인지 확인한다.
7. 자동화가 Worker A를 종료하면 Queue가 `ready=10`, `unacked=0`으로 바뀌는지 확인한다.
8. Worker B가 시작된 뒤 로그의 `redelivered=true`, `claimType=initial`을 확인한다.
9. DBeaver에서 10건 모두 `READY_FOR_PLANNING`, 후보 1,200행인지 확인한다.
10. Grafana에서 Worker가 `정상 → 중단 → 정상`, Queue 대기가 최대 10건, Worker B가 재전달 1건·최초 선점 10건·성공 10건인지 확인한다.

### 완전 수동으로 할 때 확인할 화면

1. Worker A를 `prefetch=1`, `전달 후 claim 전 지연=120초`로 시작한다.
2. PlanMate 화면에서 `장애테스트-Claim전Worker종료-01`~`10` 요청을 만든다.
3. RabbitMQ Management에서 `ready=9`, `unacked=1`이 되는 순간을 확인한다.
4. DBeaver에서 `CREATED=10`, `COLLECTING=0`, `READY=0`, Candidate=0을 확인한다.
5. 작업 관리자 또는 PowerShell에서 Worker A 프로세스만 강제 종료한다.
6. RabbitMQ가 `ready=10`, `unacked=0`으로 바뀌는지 확인한다.
7. 지연 설정이 없는 Worker B를 시작한다.
8. RabbitMQ의 redelivery 증가가 1인지 확인한다.
9. DBeaver에서 `READY_FOR_PLANNING=10`, Candidate=1,200, 중복=0을 확인한다.
10. 최종 Main Queue가 `ready=0`, `unacked=0`인지 확인한다.

## 예상 문제와 복구 방안

| 예상 문제 | 관측 방법 | 복구 방안 |
| --- | --- | --- |
| ACK 전에 Worker가 사라져 1건이 처리되지 않을 수 있음 | 종료 직전 `unacked=1` | RabbitMQ가 미확인 메시지를 Queue에 다시 넣음 |
| 재전달된 1건이 사라질 수 있음 | deliver 11, redelivery 1, ACK 10 비교 | Worker B가 재전달 메시지를 정상 소비 |
| 종료 시점이 claim 뒤라 실험 경계가 달라질 수 있음 | claim Metric과 DB 상태 확인 | 전용 지연 훅을 Listener의 delivery와 service claim 사이에 둠 |
| 같은 결과가 두 번 저장될 수 있음 | Candidate 총수와 고유 장소·순위 수 비교 | Generation 상태와 DB 고유 제약으로 한 세트만 저장 |
| 짧은 `unacked=1`을 Grafana가 놓칠 수 있음 | RabbitMQ Management API 원본 스냅샷 확인 | Grafana와 원본 JSON을 함께 증거로 사용 |

## 실제 관측에서 배운 점

- 첫 시도는 Windows에서 Worker 포트가 열리기 전 HTTP 연결이 오래 대기해 중단됐다. Health 대기 전에 TCP 포트 준비 여부를 확인하도록 자동화를 보강했다.
- 두 번째 시도는 새 여행 생성 중 외부 장소 상세 API가 60초 동안 응답하지 않아 데이터 생성 전에 중단됐다. 메시징 장애 경계와 관계없는 외부 변수를 제외하기 위해 이미 완료된 여행 10개를 재사용하고 새 Generation만 만들었다.
- Prometheus 수집 간격은 15초라 약 5초 동안 유지된 `unacked=1`을 시계열에서 놓쳤다. 이 값은 RabbitMQ Management API가 기록한 `rabbitmq-snapshots.json`으로 보완했다.

## 한계

이 결과는 로컬 Docker 환경에서 10건의 복구 정확성을 확인한 실험이며 부하 테스트가 아니다. 실제 OS 장애나 네트워크 단절 대신 실험 전용 120초 지연 훅으로 종료 위치를 고정했다. Worker A의 프로세스 강제 종료와 RabbitMQ 단일 노드 재큐잉을 검증했으며, 다중 RabbitMQ 노드 장애와 대규모 동시 소비는 별도 실험이 필요하다.

Debezium 하나가 서비스 간 멱등성을 보장하는 것은 아니다. Debezium은 DB 이벤트를 RabbitMQ까지 전달하고, 이 실험의 안전성은 RabbitMQ의 ACK·재전달과 Worker의 DB 상태·제약이 함께 만든다.

## 원본 증거

- `result.json`: 20개 자동 판정과 최종 수치
- `rabbitmq-snapshots.json`: 종료 직전 9/1, 종료 후 10/0, 최종 0/0
- `timeline.csv`: 장애·재큐잉·Health·완료 시각
- `worker-before-kill-metrics.txt`: 전달 1, claim 0, 전용 훅 1
- `worker-after-restart-metrics.txt`: 재전달 1, 최초 claim 10, 성공 10
- `worker-before-kill.out.log`: claim 전 지연 훅 진입 로그
- `worker-after-restart.out.log`: `redelivered=true`, `claimType=initial` 로그
- `queries.sql`: 대상 Generation과 Candidate 재검증 SQL
- `verification.md`: DB·RabbitMQ·Prometheus 독립 교차검증 요약
