# Retry 분류와 DLQ 격리 장애 주입 테스트 결과

- 실행 ID: `retry-classification-dlq-20260828-223237`
- 실험 시각: 2026-08-28 22:32:37 ~ 22:35:17 KST
- 시나리오별 요청: 10건, 총 20건
- 대상 커밋: `49312494b486e91e9b1ef0e55730cd366117fb88`
- 전용 실험 Queue: `planmate.reliability.exp4.20260828223237.main`
- 전용 실험 DLQ: `planmate.reliability.exp4.20260828223237.dlq`
- 최종 판정: **PASS**

## 검증 질문

> 잠시 뒤 다시 하면 성공할 수 있는 실패만 제한적으로 재시도하고, 다시 해도 성공할 수 없는 실패는 즉시 중단한 뒤 실패 메시지를 DLQ에 유실 없이 격리하는가?

## 중학생도 이해할 수 있는 설명

- `Retryable`은 일시적으로 서버가 바쁜 것처럼 **잠시 뒤 다시 해볼 가치가 있는 실패**다.
- `Non-Retryable`은 잘못된 요청처럼 **다시 해도 성공할 수 없는 실패**다.
- `DLQ`는 자동 처리를 계속하지 않고 **사람이 원인을 확인할 수 있도록 실패 메시지를 따로 보관하는 Queue**다.

이번 실험은 Retryable 10건과 Non-Retryable 10건을 같은 조건에서 비교했다. Retryable은 건당 최대 3번까지 시도했고, Non-Retryable은 첫 실패에서 바로 멈췄다.

## 장애 시뮬레이션과 예상 문제

### 4-A — Retryable 실패

실험용 장소 후보 공급자가 `PLACE_PROVIDER_UNAVAILABLE`을 발생시켰다. 외부 장소 서비스가 잠시 응답하지 않는 상황을 뜻한다.

예상 문제는 무한 Retry다. 제한 없이 계속 시도하면 Worker와 외부 API 자원을 낭비하고 뒤의 정상 메시지까지 늦어진다. 따라서 `max-attempts=3`으로 제한하고 세 번 모두 실패하면 DB를 `FAILED`로 바꾼 뒤 DLQ로 격리하도록 설계했다.

### 4-B — Non-Retryable 실패

실험용 공급자가 `PLACE_PROVIDER_REQUEST_REJECTED`를 발생시켰다. 요청 자체가 잘못돼 다시 보내도 성공할 수 없는 상황을 뜻한다.

예상 문제는 불필요한 Retry다. 이미 복구 불가능하다고 판정한 요청을 세 번 실행하면 시간과 외부 API 호출만 낭비한다. 첫 실패에서 즉시 `FAILED`로 바꾸고 DLQ로 보내도록 설계했다.

## 실제 결과

| 검증 항목 | 기대값 | 실제값 | 판정 |
| --- | ---: | ---: | :---: |
| Retryable 요청 | 10건 | 10건 | PASS |
| Retryable 전체 실패 시도 | 30회 | 30회 | PASS |
| Retryable 추가 Retry | 20회 | 20회 | PASS |
| Retryable DB `FAILED` | 10건 | 10건 | PASS |
| Retryable 실패 코드 | `PLACE_PROVIDER_UNAVAILABLE` 10건 | 10건 | PASS |
| Retryable DLQ | 10건 | 10건 | PASS |
| Non-Retryable 요청 | 10건 | 10건 | PASS |
| Non-Retryable 전체 실패 시도 | 10회 | 10회 | PASS |
| Non-Retryable 추가 Retry | 0회 | 0회 | PASS |
| Non-Retryable DB `FAILED` | 10건 | 10건 | PASS |
| Non-Retryable 실패 코드 | `PLACE_PROVIDER_REQUEST_REJECTED` 10건 | 10건 | PASS |
| 전용 DLQ 최종 | 20건 | 20건 | PASS |
| DLQ의 고유 Generation ID | 20개 | 20개 | PASS |
| RabbitMQ `x-death` 이력 | 20/20건 | 20/20건 | PASS |
| Candidate 저장 / READY 전환 | 0 / 0건 | 0 / 0건 | PASS |
| 전용 Main Queue 최종 | Ready 0·Unacked 0 | 0·0 | PASS |
| 운영 Main Queue 최종 | Ready 0·Unacked 0 | 0·0 | PASS |
| 기존 운영 DLQ 변화 | 0건 | 167 → 167, 변화 0건 | PASS |

### 숫자 계산

- Retryable 전체 시도 30 = 최초 시도 10 + 추가 Retry 20
- Retryable Retry 20 = 10건 × 추가 2회
- Non-Retryable 전체 시도 10 = 10건 × 최초 1회
- Non-Retryable Retry 0 = 복구 불가능 분류 직후 중단
- 전용 DLQ 20 = Retryable 10 + Non-Retryable 10

## 처리 시간

| 구분 | Worker 시작부터 DB `FAILED` 10건 확인까지 |
| --- | ---: |
| Retryable | **44.972초** |
| Non-Retryable | **23.667초** |

이 값은 성능 비교를 위한 부하 테스트 수치가 아니다. 각 실패 시도에 1초의 통제된 지연을 넣고, Worker 기동 시간까지 포함한 로컬 실험의 완료 시간이다.

## 내가 직접 화면을 보며 재현하는 방법

아래 순서는 자동화가 수행하는 것과 같은 대상을 Docker Desktop, PlanMate, DBeaver, RabbitMQ, Grafana에서 직접 관찰하는 방법이다.

1. Docker Desktop에서 PostgreSQL, RabbitMQ, Debezium, Grafana, Prometheus가 모두 실행 중인지 확인한다.
2. PowerShell에서 `./scripts/reliability-tests/Invoke-RetryClassificationDlq.ps1`을 실행한다.
3. PlanMate 화면에서 `장애테스트-재시도가능실패-01`~`10`, `장애테스트-재시도불가실패-01`~`10` 여행이 생성됐는지 확인한다.
4. RabbitMQ Management의 Queues 화면에서 이름이 `planmate.reliability.exp4.`로 시작하는 전용 Main Queue와 DLQ를 찾는다.
5. 4-A가 끝나면 전용 DLQ가 10건인지 확인한다.
6. 4-B가 끝나면 전용 DLQ가 20건이고 전용 Main Queue가 0건인지 확인한다.
7. DBeaver에서 아래 Query를 실행해 실패 코드별로 10건씩인지 확인한다.
8. Grafana의 `PlanMate 장애실험 04 · Retry 분류와 DLQ 격리`를 열어 ①~⑤ 패널을 확인한다.
9. `result.json`의 `verdict`가 `PASS`이고 `failedChecks`가 빈 배열인지 확인한다.

```sql
SELECT status, failure_reason, count(*)
FROM itinerary_generations
WHERE id BETWEEN 1176 AND 1195
GROUP BY status, failure_reason
ORDER BY failure_reason;

SELECT count(*) AS candidate_rows
FROM place_candidates
WHERE generation_id BETWEEN 1176 AND 1195;
```

기대 DB 결과는 `FAILED + PLACE_PROVIDER_UNAVAILABLE = 10`, `FAILED + PLACE_PROVIDER_REQUEST_REJECTED = 10`, Candidate 0행이다.

## 실험 안전장치

- 실행 시각이 포함된 전용 Exchange·Queue·DLQ를 사용했다.
- 운영 Main Queue는 실험 전후 Ready 0·Unacked 0을 확인했다.
- 기존 운영 DLQ 167건은 소비하거나 삭제하지 않았다.
- 실험 메시지 20건만 Generation ID로 검증해 전용 Queue로 옮겼다.
- 실험 Worker는 4-A와 4-B마다 새로 실행해 Metric을 0부터 분리 수집했다.
- 전용 DLQ 메시지는 `ack_requeue_true`로 조회해 증거 수집 뒤에도 20건을 그대로 유지했다.

## 교차검증 증거

| 증거 | Retryable | Non-Retryable |
| --- | ---: | ---: |
| Worker 실패 시도 로그 | 30줄 | 10줄 |
| Worker 최종 FAILED 로그 | 10줄 | 10줄 |
| 실패 시도 Metric | 30 | 10 |
| Retry Metric | 20 | 0 |
| DB `FAILED` | 10 | 10 |
| DLQ 메시지 | 10 | 누적 20 |

로그·Metric·DB·RabbitMQ 네 자료가 같은 수치를 가리키므로 한 도구의 표시 오류만으로 PASS가 나온 결과가 아니다.

## Grafana 시각화 증거

Grafana Dashboard 원본: `infra/grafana/dashboards/planmate-reliability-experiment-04.json`

1. `images/01-장애범위-인프라정상-Worker분리실행.png`
2. `images/02-Retryable-시도30-Retry20.png`
3. `images/03-Retryable-기대값-실제값.png`
4. `images/04-NonRetryable-시도10-Retry0.png`
5. `images/05-NonRetryable-기대값-실제값.png`
6. `images/06-DLQ-0에서10에서20.png`
7. `images/07-최종판정-증거교차검증.png`

핵심 결과는 `01 장애 범위 → 02 Retryable → 04 Non-Retryable → 06 DLQ → 07 최종 판정` 순서로 확인할 수 있다. 세부 수치는 03과 05의 기대값·실제값 표에서 확인한다.

## 실험 중 발견한 문제와 개선

첫 사전 실행 `retry-classification-dlq-20260828-223019`은 이전 API 프로세스의 출력 파이프가 막혀 첫 여행 생성 요청이 60초 타임아웃됐다. DB를 확인하니 테스트 여행은 0건이었고 전용 Queue 생성 전이었다. 해당 Run을 `PRECHECK_FAILED`와 공식 결과 사용 금지로 표시하고, API를 파일 로그 방식으로 재기동한 뒤 새 Run으로 다시 수행했다.

코드에는 다음 관측 기능을 추가했다.

- 실패 시도마다 `generationId`, `tripId`, `attempt`, `maxAttempts`, `classification`, `failureCode` 구조화 로그
- `failure.attempt` Metric에 `classification`·`failureCode` 태그
- `retry` Metric에 `classification`·`failureCode` 태그
- 결과 그래프에서 Retryable 30/20과 Non-Retryable 10/0을 분리 표시

## 한계

- 로컬 Docker 환경의 시나리오별 10건 정확성 실험이며 부하 테스트가 아니다.
- 실제 Google Places 장애가 아니라 같은 예외 분류 경로를 사용하는 결정적 실험 공급자로 실패를 주입했다.
- 처리 시간에는 Worker 기동과 시도당 1초의 통제 지연이 포함됐다.
- Grafana 시계열에는 Prometheus 15초 수집 간격의 오차가 있다. 정확한 횟수는 Worker endpoint Metric 원본과 로그를 함께 사용했다.
- DLQ에서 수동 재처리하거나 원인을 수정한 뒤 복구하는 운영 절차는 이번 실험 범위가 아니다.

## 원본 파일

| 파일 | 용도 |
| --- | --- |
| `result.json` | 모든 정량값과 최종 PASS Check |
| `timeline.csv` | 4-A·4-B 실제 시각 |
| `created-items.json` | 20개 테스트명·Trip·Generation ID |
| `database-snapshots.json` | 생성 직후와 최종 DB 집계 |
| `rabbitmq-snapshots.json` | 운영/전용 Queue 전·후 값 |
| `retryable-metrics.prom` | Retryable Worker Metric 원본 |
| `non-retryable-metrics.prom` | Non-Retryable Worker Metric 원본 |
| `retryable-worker.out.log` | 30회 시도·10회 FAILED 로그 |
| `non-retryable-worker.out.log` | 10회 시도·10회 FAILED 로그 |
| `dlq-messages.json` | 전용 DLQ 20건과 `x-death` Header |
| `queries.sql` | DB 재검증 Query |
