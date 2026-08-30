# PlanMate 장애 주입 실험 1~7 최종 일관성 감사

> 감사일: 2026-08-29  
> 목적: 포트폴리오에 옮기기 전에 판정·숫자·용어·이미지 경로가 서로 모순되지 않는지 확인한다.

## 1. 감사 결론

- 공식 Run 7개의 `result.json` 판정: **모두 PASS**
- 공식 Run 7개의 `failedChecks`: **모두 0개**
- 결과 가이드가 참조하는 저장소 증거·최종 문서 경로: **55개 모두 존재**
- 포트폴리오용 이미지: 실험별 폴더로 분리되어 있음
- 실험 1~7의 핵심 수치와 통합 원고의 수치: 일치
- 과장 위험이 있는 표현: 교정 완료

이 감사는 포트폴리오 문구와 저장된 실험 증거의 일관성을 확인한다. 실험 자체의 운영 환경 재수행을 대신하는 것은 아니다.

## 2. 공식 Run 고정

| 실험 | 공식 Run ID | Verdict | Failed checks |
| ---: | --- | --- | ---: |
| 1 | `debezium-stop-restart-20260828-173040` | PASS | 0 |
| 2 | `worker-before-ack-termination-20260828-211457` | PASS | 0 |
| 3 | `cdc-offset-mismatch-replay-20260828-220606` | PASS | 0 |
| 4 | `retry-classification-dlq-20260828-223237` | PASS | 0 |
| 5 | `stale-generation-recovery-20260828-225751` | PASS | 0 |
| 6 | `rabbitmq-outage-recovery-20260828-233213` | PASS | 0 |
| 7 | `worker-before-claim-termination-20260829-002951` | PASS | 0 |

공식 결과를 인용할 때는 중간 실패 Run이나 예비 Run이 아니라 위 ID만 사용한다.

## 3. 핵심 수치 대조

| 실험 | 입력·대상 | 전달 계층 | Worker·DB 결과 | 복구 시간 |
| ---: | --- | --- | --- | --- |
| 1 | 요청/Outbox 10/10 | 발행/전달/ACK 10/10/10 | READY 10, 후보 1,200, 유실 0 | Connector 9.818초, 적체 37.323초 |
| 2 | 요청 10 | 발행/전달/ACK 10/11/10, 재전달 1 | SKIP 1, READY 10, 후보 1,200, 중복 0 | 재큐잉 4.063초, Health 13.425초, 적체 17.617초 |
| 3 | 원본/Replay 10/10 | 논리적 중복 10, Rabbit 재전달 0 | SKIP 10, READY 10 유지, 후보 1,200 유지 | 감지 7.637초, Detector 7.393초, Replay 10.727초 |
| 4 | Retryable/Non-Retryable 각 10 | 실험 DLQ 20 | 시도 30/10, Retry 20/0, FAILED 10/10 | 44.972초/23.667초 |
| 5 | Generation 10 | 발행/전달/ACK 20/20/20 | 새 Claim 10, Fencing 10, READY 10, 중복 0 | Worker B 13.687초, Lease 후 13.688초 |
| 6 | 요청/Outbox 10/10 | 실패 시도 54, 복구 발행/전달/ACK 10/10/10 | READY 10, 후보 1,200, 유실 0 | Broker 28.541초, Health 5.416초, Debezium 10.324초, 적체 15.905초 |
| 7 | 새 Generation 10 | 발행/전달/ACK 10/11/10, 재전달 1 | 최초 Claim 10, SKIP 0, READY 10, 후보 1,200 | 재큐잉 4.563초, Health 11.955초, 적체 14.074초 |

## 4. 의미 충돌 감사

### Debezium과 멱등성

교정 전 위험 표현:

> Debezium이 서로 다른 두 서비스 간 멱등성을 보장한다.

최종 표현:

> Outbox와 Debezium은 커밋된 이벤트의 전달 가능성을 지키고, RabbitMQ ACK·재전달과 Worker 상태·Claim Version·DB 제약이 중복 전달의 최종 결과 중복을 막는다.

### 실험 2·3·7의 재전달

- 실험 2: RabbitMQ 재전달 1건. DB는 이미 완료됐으므로 SKIP 1건.
- 실험 3: CDC가 과거 이벤트 10건을 새로 Replay. RabbitMQ 재전달 Flag는 0건이고 SKIP은 10건.
- 실험 7: RabbitMQ 재전달 1건. DB Claim 전이므로 SKIP하지 않고 최초 처리, Claim은 총 10건.

세 실험의 전달 횟수만 보고 같은 장애라고 설명하면 안 된다.

### 실험 4의 DLQ

- 실험용 DLQ 증가: 0 → 10 → 20건
- 기존 운영용 DLQ: 167 → 167건

`DLQ 20건`과 `운영 DLQ 증가 0건`은 서로 모순이 아니다. 서로 다른 Queue를 가리킨다.

### 실험 6의 실패 54회

54는 유실 이벤트 수가 아니라 RabbitMQ 중단 중 Debezium Sink가 반복한 **발행 실패 시도 횟수**다. 고유 Outbox 이벤트는 10건이고 복구 후 고유 이벤트 10건이 전달됐다.

### 실험 7의 Trip 재사용

외부 장소 API 변수를 피하려고 기존 Trip 10개를 재사용했지만 Generation 10개와 비동기 작업은 새로 생성했다. 포트폴리오에는 `여행 전체를 새로 만들었다`고 쓰지 않고 `기존 Trip에서 새 Generation 10건을 생성했다`고 쓴다.

## 5. 포트폴리오 범위 감사

다음 표현은 증거 범위를 넘어가므로 사용하지 않는다.

- Exactly-once를 보장했다.
- 모든 비동기 장애를 검증했다.
- 운영 환경의 장애 복구를 보장한다.
- Debezium만으로 결과 멱등성이 완성된다.
- `@Transactional`만으로 DB와 RabbitMQ 전체가 하나의 원자적 트랜잭션이 된다.

허용되는 결론은 다음과 같다.

> 로컬 Docker 기반의 현재 아키텍처에서 DB Commit부터 RabbitMQ ACK까지 정의한 주요 실패 경계에 장애를 주입했다. 일곱 공식 Run이 모두 PASS했고, 정상 완료 시나리오에서 이벤트 유실 0건과 후보 데이터 중복 0건을 확인했다.

## 6. 이미지 감사 기준

포트폴리오 본문은 실험별 2~3장의 핵심 이미지만 사용한다.

1. 장애 범위: 무엇이 DOWN이고 무엇이 정상인지 보인다.
2. 복구 과정: 대기 → 재전달/재처리 → 완료 흐름이 보인다.
3. 정량 판정: 기대값과 실제값, 유실·중복, PASS가 잘리지 않고 보인다.

다음 항목을 배포 전 화면 너비에서 다시 확인한다.

- 숫자 1·2·3 등의 순서 표시는 같은 폰트 크기인가
- `정상`, `중단`, 기대값, 실제값과 설명 문장이 잘리지 않는가
- 내부 코드값 옆에 사람이 읽을 수 있는 한글 설명이 있는가
- 전달 수와 최종 데이터 수를 혼동하지 않게 표시했는가
- Grafana 원본 시간 범위와 공식 Run 시간이 일치하는가

## 7. 공개 검증 자료

- 이미지 인덱스: `docs/reliability-tests/portfolio-images-index.md`
- 공식 원본: `docs/reliability-tests/runs/<공식 Run ID>/result.json`

실험 수치는 각 공식 Run의 `README.md`와 `result.json`을 기준으로 확인한다.
