# 실험 7 독립 교차검증

자동화의 `result.json` 판정만 믿지 않고 DB, RabbitMQ, Worker 로그, Prometheus를 각각 다시 확인했다.

## DB

- Generation ID: `1226`~`1235`
- `READY_FOR_PLANNING`: 10건
- `FAILED`: 0건
- Candidate: 1,200행
- `(generation_id, place_id)` 고유 행: 1,200행
- `(generation_id, rank)` 고유 행: 1,200행
- Generation별 Candidate 최소/최대: 120/120행

따라서 후보 결과는 Generation마다 정확히 한 세트이며 중복 행은 0건이다.

## RabbitMQ

| 관측 시점 | ready | unacked | publish 누적 | deliver 누적 | ACK 누적 | redelivery 누적 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 요청 전 | 0 | 0 | 10 | 10 | 10 | 0 |
| Worker A 종료 직전 | 9 | 1 | 20 | 11 | 10 | 0 |
| Worker A 종료 후 | 10 | 0 | 20 | 11 | 10 | 0 |
| Worker B 처리 후 | 0 | 0 | 20 | 21 | 20 | 1 |

실험 증가량은 publish 10, deliver 11, ACK 10, redelivery 1이다. `deliver=11`은 최초 전달 10회와 ACK되지 않은 1건의 재전달 1회다.

운영 DLQ는 167건에서 167건으로 유지되어 증가량은 0건이다.

## Worker 로그

- Worker A: `generationId=1226`, `redelivered=false`, `RELIABILITY_HOOK_AFTER_DELIVERY_BEFORE_CLAIM`
- Worker B: `generationId=1226`, `redelivered=true`
- Worker B: `claimVersion=1`, `claimType=initial`, `redelivered=true`
- Worker B: 동일 Generation 처리 후 ACK 반환

종료 대상은 Worker A에서 claim하지 않았으므로 Worker B에서 `SKIP`이 아니라 최초 claim으로 처리됐다.

## Prometheus

| 시각(KST) | 관측값 |
| --- | --- |
| 00:30:11 | Worker 정상 1 |
| 00:30:26 | Worker 중단 0 |
| 00:30:41 | Worker 정상 1 |
| 00:30:19 | Main Queue ready 10 |
| 00:30:49 | Main Queue ready 0 |

Worker B Metric은 재전달 1, 최초 claim 10, 처리 성공 10, SKIP 0이다. Worker A Metric은 최초 전달 1, claim 0, claim 전 장애 훅 1이다.

Prometheus의 15초 수집 간격은 약 5초간 유지된 `unacked=1`을 포착하지 못했다. 해당 값은 RabbitMQ Management API의 종료 직전 스냅샷으로 검증했다.

## 최종 판정

DB 완료 10, RabbitMQ ACK 10, Candidate 1,200행이 일치한다. 이벤트 유실 0, Candidate 중복 0, DLQ 증가 0이며 자동 검사 20개가 모두 통과했으므로 최종 판정은 **PASS**다.
