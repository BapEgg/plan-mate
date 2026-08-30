# 장애 테스트 03 — CDC Offset 불일치 탐지와 과거 이벤트 재전달

## 최종 판정

**PASS**

> PostgreSQL replication slot보다 오래된 Debezium Offset를 주입하자 Connector가 읽을 수 없는 위치를 7.637초 만에 탐지하고 Health `DOWN`으로 멈췄다. 최신 Offset 체크포인트를 복원한 뒤 7.393초 만에 `UP`으로 회복했다. 이어 이미 처리된 같은 이벤트 10건을 다시 전달했지만 Worker가 모두 `SKIP`하여 READY 10/10, Candidate 1,200행을 그대로 유지했고 유실·중복·DLQ 증가는 0건이었다.

| 항목 | 기록 |
| --- | --- |
| 실행 ID | `cdc-offset-mismatch-replay-20260828-220606` |
| 실험 시각 | 2026-08-28 22:06:06 ~ 22:08:39 KST |
| 대상 커밋 | `49312494b486e91e9b1ef0e55730cd366117fb88` |
| Offset 불일치 전략 | `trust_offset` |
| 3-A Snapshot mode | `no_data` |
| 3-B Replay Snapshot mode | `initial` |
| 격리 범위 | 폐기 가능한 PostgreSQL 컨테이너, 고유 slot/publication, 별도 Offset 디렉터리 |
| 재전달 검증 대상 | 이미 READY인 Generation `1166` ~ `1175`, 총 10건 |

## 검증 질문

1. PostgreSQL의 읽기 위치와 Debezium의 처리 위치가 맞지 않을 때 조용히 데이터를 건너뛰지 않고 오류로 차단하는가?
2. 검증된 최신 Offset를 복원하면 Connector가 다시 정상화되는가?
3. 복구 과정에서 같은 과거 이벤트 10건이 다시 전달돼도 DB 상태와 후보 데이터가 중복되지 않는가?

## 중학생도 따라갈 수 있는 수동 테스트 순서

이 실험은 Offset 파일과 replication slot을 다루므로 기존 `planmate-postgres`나 `planmate-debezium`에서 직접 수행하면 안 된다. 반드시 실험 전용 컨테이너에서 진행한다.

### 3-A — 오래된 Offset를 넣어 보호 동작 확인

1. 실험 전용 PostgreSQL과 Debezium을 새로 띄운다.
2. 테스트 Outbox 이벤트 1건을 만들고 RabbitMQ에 전달된 것을 확인한다.
3. Debezium을 멈추고 현재 Offset 파일을 `체크포인트 1`로 복사한다.
4. Debezium을 다시 켜고 Outbox 이벤트 1건을 더 만든다.
5. 두 번째 이벤트까지 전달되면 Debezium을 멈추고 Offset 파일을 `체크포인트 2`로 복사한다.
6. DBeaver에서 replication slot의 `confirmed_flush_lsn`이 체크포인트 1보다 앞으로 이동했는지 확인한다.
7. 현재 Offset 파일을 과거의 `체크포인트 1`로 바꾸고 Debezium을 시작한다.
8. Grafana에서 실험 CDC가 `정상 → 중단`으로 바뀌는지 확인한다.
9. Debezium 로그에서 `Last recorded offset is no longer available on the server`를 확인한다.
10. Debezium을 멈추고 최신 `체크포인트 2`를 복원한 다음 다시 시작한다.
11. Grafana와 Health endpoint에서 `중단 → 정상`을 확인한다.

### 3-B — 같은 과거 이벤트 10건을 다시 보내 멱등성 확인

1. 이미 `READY_FOR_PLANNING`인 Generation 10건을 준비한다.
2. 각 Generation을 가리키는 Outbox 이벤트 10건을 실험 DB에 넣는다.
3. 최초 전달 10건의 `generationId`를 저장한다.
4. 새 replication slot을 만들고 `snapshot.mode=initial`인 Debezium을 시작한다.
5. 같은 Outbox 10건이 PlanMate RabbitMQ Queue로 다시 전달되는지 확인한다.
6. Worker 로그와 Metric에서 `SKIP 10`을 확인한다.
7. DBeaver에서 10건이 모두 `READY_FOR_PLANNING`으로 유지되는지 확인한다.
8. Candidate가 실험 전후 모두 1,200행인지 확인한다.
9. RabbitMQ Main Queue가 ready 0·unacked 0인지 확인한다.
10. DLQ 증가와 이벤트 유실이 0건인지 확인한다.

자동 실행 명령:

```powershell
.\scripts\reliability-tests\Invoke-CdcOffsetMismatchReplay.ps1 -RequestCount 10 -RecoveryTimeoutSeconds 180
```

## 예상 문제와 복구 방안

| 장애 상황 | 예상 문제 | 안전한 복구 방안 |
| --- | --- | --- |
| Debezium Offset가 slot보다 뒤에 있음 | 서버가 더 이상 보관하지 않는 변경 구간이 생겨 조용한 유실 가능 | `trust_offset`가 시작을 차단하게 하고, 로그와 LSN을 확인한 뒤 검증된 최신 Offset 복원 |
| Offset 파일만 임의 삭제 | Snapshot 정책에 따라 과거 이벤트 대량 재전달 또는 시작 실패 | 원본 파일·slot LSN을 먼저 보관하고 폐기 가능한 환경에서만 복구 연습 |
| 새 slot + initial snapshot | 이미 처리한 Outbox 이벤트가 다시 발행됨 | Worker가 Generation의 현재 상태를 확인해 이미 READY면 `SKIP` |
| 중복 이벤트를 다시 처리 | Candidate 중복 저장 또는 상태 퇴행 가능 | 멱등 Claim과 DB 유니크 제약을 함께 검증 |

## 실제 결과

### 3-A — Offset 불일치 탐지와 복구

| 검증 항목 | 기대값 | 실제값 | 판정 |
| --- | ---: | ---: | :---: |
| 체크포인트 사이 slot 전진 | 전진 | `0/19409D0 → 0/1940B90` | PASS |
| 오래된 Offset 명시적 탐지 | 오류 발생 | 오류 로그 확인 | PASS |
| 불일치 상태 Health | DOWN | DOWN | PASS |
| 불일치 탐지 시간 | 측정 | **7.637초** | PASS |
| 최신 Offset 복원 후 Health | UP | UP | PASS |
| Connector 복구 시간 | 측정 | **7.393초** | PASS |

실제 보호 로그:

```text
Last recorded offset is no longer available on the server.
The connector is trying to read change stream ... but this is no longer available on the server.
```

### 3-B — 과거 이벤트 10건 재전달

| 검증 항목 | 기대값 | 실제값 | 판정 |
| --- | ---: | ---: | :---: |
| 최초 전달 | 10건 | 10건 | PASS |
| Snapshot 재전달 | 10건 | 10건 | PASS |
| 원본·재전달 Generation 집합 | 완전 일치 | `1166` ~ `1175` 완전 일치 | PASS |
| RabbitMQ Publish / Deliver / ACK 증가 | 10 / 10 / 10 | 10 / 10 / 10 | PASS |
| Worker 멱등 SKIP | 10건 | 10건 | PASS |
| READY 상태 | 10/10 유지 | 10/10 유지 | PASS |
| Candidate 행 | 1,200 유지 | 1,200 → 1,200 | PASS |
| Candidate·Rank 중복 | 0 / 0행 | 0 / 0행 | PASS |
| 상태 퇴행·RabbitMQ Redelivery | 0 / 0건 | 0 / 0건 | PASS |
| 이벤트 유실·DLQ 증가 | 0 / 0건 | 0 / 0건 | PASS |
| Main Queue 최종 상태 | ready 0·unacked 0 | ready 0·unacked 0 | PASS |
| 과거 이벤트 처리 완료 시간 | 측정 | **10.727초** | PASS |

기존 DLQ의 절대값은 167건이었고 실험 후에도 167건이었다. 이 실험의 판정에는 절대값이 아니라 **증가량 0건**을 사용했다.

## 실제 타임라인

| 시각(KST) | 사건 |
| --- | --- |
| 22:06:22.864 | 실험 CDC Detector 최초 Health UP |
| 22:06:30.432 | Offset 체크포인트 1 저장 |
| 22:06:50.821 | 체크포인트 2 저장, slot이 최신 위치로 전진 |
| 22:06:50.823 | 과거 Offset 체크포인트 1 주입 후 재시작 |
| 22:06:58.460 | 실제 불일치 오류와 Health DOWN 확인 |
| 22:07:51.370 | 최신 Offset 복원 후 Health UP |
| 22:08:09.010 | 이미 처리된 Generation용 Outbox 10건 생성 |
| 22:08:25.983 | 멱등성 검증 Worker Health UP |
| 22:08:33.015 | 새 slot의 Replay Connector Health UP |
| 22:08:36.711 | 재전달 10건·Worker SKIP 10건·Queue 안정화 확인 |

## 포트폴리오 이미지

1. `images/cdc-offset-01-failure-scope.png`
   - 운영 API·RabbitMQ·운영 Debezium은 정상이고 실험 CDC만 `정상 → 중단 → 정상`으로 바뀐 증거
2. `images/cdc-offset-02-slot-offset-recovery.png`
   - 과거 Offset와 최신 slot의 LSN 불일치, 차단, 최신 체크포인트 복원 과정
3. `images/cdc-offset-03-verdict.png`
   - 탐지·복구·재전달·SKIP·READY·유실·DLQ의 기대값과 실제값
4. `images/cdc-offset-04-replay-skip.png`
   - 같은 Generation 10건, RabbitMQ 10/10/10, SKIP 10, Candidate 추가 0
5. `images/cdc-offset-05-recovery-explanation.png`
   - 조용한 유실 차단과 멱등 복구 원리를 처음 보는 사람에게 설명

Grafana 대시보드 원본: `infra/grafana/dashboards/planmate-reliability-experiment-03.json`

## 교차 검증한 원본 증거

| 파일 | 확인 내용 |
| --- | --- |
| `result.json` | 18개 PASS 조건, 최종 판정, 측정 시간 |
| `timeline.csv` | 장애 주입·탐지·복구·Replay 시각 |
| `replication-slot-snapshots.json` | 체크포인트별 restart/confirmed LSN |
| `detect-mismatch.log` | 실제 Offset 불일치 WARN/ERROR |
| `event-comparison.json` | 최초·재전달 Generation ID 집합 일치 |
| `original-delivery-messages.json` | 최초 전달 10건 원문 |
| `replayed-snapshot-messages.json` | Snapshot 재전달 10건 원문 |
| `rabbitmq-snapshots.json` | Publish·Deliver·ACK·DLQ 전후 값 |
| `replay-worker.out.log` | 재전달 메시지의 멱등 SKIP 처리 |

## 실험 중 발견한 문제와 개선

첫 실행 `cdc-offset-mismatch-replay-20260828-220239`에서는 자동화가 Connector 시작 로그의 `offset mismatch strategy` 설명을 실제 오류로 너무 일찍 인식해 탐지 시간을 `0.620초`로 잘못 기록했다. 교차 검증에서 실제 WARN/ERROR가 약 8초 뒤 발생한 것을 찾아냈고, 실제 문장인 `Last recorded offset is no longer available`만 타이머 종료 조건으로 인정하도록 수정했다.

따라서 첫 실행은 `SUPERSEDED`로 표시하고 포트폴리오에서 제외한다. 이 문서의 두 번째 실행만 공식 결과이며, 정확한 탐지 시간은 **7.637초**다.

Grafana state timeline은 짧은 마지막 정상 구간에 `정상` 글자를 강제로 표시하면 글자가 잘렸다. 표시 범위를 의도적 종료 전 22:08:12까지 넓히고 값 표시를 `auto`로 변경해 `정상/중단/정상`이 모두 온전히 보이도록 수정했다.

## 한계

- 로컬 Docker Desktop에서 수행한 10건 규모의 복구 정확성 실험이며 부하 테스트가 아니다.
- Offset 불일치는 폐기 가능한 실험 PostgreSQL에서 재현했다. 운영 DB의 Offset 파일이나 slot은 변경하지 않았다.
- Prometheus 수집 간격이 15초이므로 Grafana 상태 전환에는 최대 약 15초의 관측 오차가 있다. 7.637초와 7.393초는 Health endpoint와 실제 오류 로그 시각 기준이다.
- Replay Worker 실행 시간이 짧아 `SKIP 10` 누적값은 Worker endpoint와 로그에는 남았지만 Prometheus 정기 scrape 시계열에는 남지 않았다. Grafana 판정 카드는 `result.json`, Worker 로그, RabbitMQ API, DB 행 수의 교차 검증값을 표시한다.
- 기존 DLQ 절대값 167건은 과거 실험 데이터다. 이 실험에서는 전후 증가량 0건만 판정에 사용했다.
