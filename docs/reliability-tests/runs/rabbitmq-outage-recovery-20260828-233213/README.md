# 장애 테스트 06 — RabbitMQ 중단 중 CDC 발행 실패와 복구

> 공식 Run: `rabbitmq-outage-recovery-20260828-233213`  
> 실행 시각: 2026-08-28 23:32:13~23:33:21 KST  
> 최종 판정: **PASS**

## 중학생도 이해할 수 있는 한 문장

메시지를 받는 RabbitMQ가 멈춰도 Debezium은 “보냈다”라고 잘못 기록하지 않았고, RabbitMQ가 다시 켜진 뒤 기다리던 10건을 모두 전달했다.

## 이 실험이 확인한 순서

1. 사용자가 여행 생성 요청 10건을 보낸다.
2. API는 DB와 Outbox에 요청을 저장한다.
3. Debezium은 Outbox 이벤트를 읽지만, 중단된 RabbitMQ에는 전달할 수 없다.
4. 이때 Debezium Offset과 PostgreSQL Replication Slot이 움직이지 않는지 확인한다.
5. RabbitMQ를 다시 켠다.
6. Debezium이 같은 위치부터 10건을 전달하고 Worker가 처리한다.
7. DB·RabbitMQ·Audit Queue의 수를 비교해 유실과 중복을 판정한다.

## 장애 시뮬레이션 → 예상 문제 → 복구 방안 → 실제 결과

| 구분 | 내용 |
| --- | --- |
| 장애 시뮬레이션 | RabbitMQ 컨테이너만 중단한 상태에서 `장애테스트-래빗엠큐중단-01`~`10` 생성 |
| 예상 문제 | Debezium이 전송 실패를 성공으로 오인하면 Offset이 앞서가고, Outbox 이벤트가 RabbitMQ에 전달되지 않은 채 유실될 수 있음 |
| 복구 방안 | 전송 실패 중 Offset/Slot 확정 위치를 유지하고, RabbitMQ 복구 후 미전송 위치부터 다시 발행 |
| 실제 결과 | Outbox 10건 보존, Offset/Slot 정지, 재기동 후 발행·전달·ACK 10/10/10, READY 10/10, 유실 0, 중복 0 |

## 실제 측정값

| 판정 항목 | 기대값 | 실제값 | 판정 |
| --- | ---: | ---: | :---: |
| 테스트 요청 | 10건 | 10건 | PASS |
| 중단 중 Outbox 생성 | 10건 | 10건 | PASS |
| 중단 중 전달 대기 | 10건 | 10건 | PASS |
| Debezium Sink 오류 | 1회 이상 | 54회 감지 | PASS |
| RabbitMQ 발행 | 10건 | 10건 | PASS |
| Worker 전달 | 10건 | 10건 | PASS |
| 처리 완료 ACK | 10건 | 10건 | PASS |
| Audit Queue 이벤트 | 10건 | 10건 | PASS |
| 고유 Generation ID | 10개 | 10개 (`1216`~`1225`) | PASS |
| READY 전환 | 10/10 | 10/10 | PASS |
| Candidate 저장 | 1,200행 | 1,200행 | PASS |
| 장소/순위 중복 | 0/0행 | 0/0행 | PASS |
| 이벤트 유실 | 0건 | 0건 | PASS |
| 운영 DLQ 증가 | 0건 | 0건 (`167 → 167`) | PASS |
| Main Queue 잔여 Ready/Unacked | 0/0건 | 0/0건 | PASS |

## 복구 시간

| 구간 | 실측값 | 뜻 |
| --- | ---: | --- |
| RabbitMQ 중단 시간 | 28.541초 | 메시지 Broker가 실제로 사용할 수 없었던 시간 |
| RabbitMQ Start → Health UP | 5.416초 | 컨테이너 시작 후 정상 응답까지 걸린 시간 |
| RabbitMQ 복구 → Debezium 복구 | 10.324초 | Broker가 돌아온 뒤 CDC 발행기가 정상화될 때까지 걸린 시간 |
| RabbitMQ Start → READY 10/10 | 15.905초 | 재기동부터 10건의 후보 수집이 모두 끝날 때까지 걸린 전체 시간 |
| Queue 정리 | 0.017초 | 마지막 처리 후 Main Queue가 0건이 되는 데 걸린 시간 |

Prometheus 스크레이프 간격 때문에 Grafana의 DOWN/UP 경계는 실제 Docker 시각과 최대 15초 차이날 수 있다. 시간 판정은 `timeline.csv`와 `result.json`을 기준으로 한다.

## 전달 위치가 멈췄다는 증거

| 관측 지점 | 중단 전 | 중단 중 | 복구 후 | 해석 |
| --- | --- | --- | --- | --- |
| DB 현재 WAL 위치 | `0/2D94B48` | `0/2DA4900` | `0/2E8DB50` | 요청 저장으로 DB 자체는 실제 변경됨 |
| Debezium Offset SHA-256 | `dc8423…60897` | `dc8423…60897` | `1b37b2…5bda2` | 중단 중에는 처리 위치를 확정하지 않고 복구 후에만 전진 |
| Replication Slot 확인 위치 | `0/2C8EF68` | `0/2C8EF68` | `0/2DA3E90` | 보내지 못한 WAL 구간을 건너뛰지 않음 |

택배로 비유하면 DB에는 새 택배 10개가 생겼지만 받을 창고인 RabbitMQ가 닫혀 있었다. Debezium은 배송 완료 도장을 찍지 않았고, 창고가 다시 열리자 마지막 책갈피 위치부터 10개를 보냈다.

## 실제 타임라인

| 시각(KST) | 사건 |
| --- | --- |
| 23:32:35 | RabbitMQ 중단 |
| 23:32:53 | 테스트 요청·Outbox 10건 저장 |
| 23:33:00 | Debezium 발행 실패, Offset/Slot 유지 확인 |
| 23:33:03 | RabbitMQ 재기동 |
| 23:33:09 | RabbitMQ Health 정상 |
| 23:33:19 | 10건 모두 `READY_FOR_PLANNING` |
| 23:33:21 | Queue 0, 유실 0, 중복 0, 최종 PASS |

## 직접 다시 테스트하는 방법

### 사람이 화면을 보며 진행할 때

1. Docker Desktop에서 PostgreSQL, API, Debezium, RabbitMQ, Prometheus, Grafana가 정상인지 확인한다.
2. Docker Desktop에서 `planmate-rabbitmq` 컨테이너의 Stop 버튼을 누른다.
3. PlanMate 화면에서 `장애테스트-래빗엠큐중단-01`~`10` 여행을 만든다.
4. DBeaver에서 Outbox 10건과 `itinerary_generations.status = CREATED` 10건을 확인한다.
5. Grafana에서 API는 정상이고 RabbitMQ만 중단 상태인지 확인한다.
6. Docker Desktop에서 `planmate-rabbitmq`를 Start한다.
7. Grafana에서 RabbitMQ `중단 → 정상`, 요청 `전달 대기 10 → 완료 10`, 발행·전달·ACK `10/10/10`을 확인한다.
8. DBeaver에서 `READY_FOR_PLANNING` 10건, Candidate 1,200행, 중복 0행을 확인한다.

RabbitMQ 중단 중에는 Management 화면도 열리지 않는 것이 정상이다. 이 구간은 DBeaver와 Grafana로 관측한다.

### PowerShell 자동 실험

```powershell
pwsh ./scripts/reliability-tests/Invoke-RabbitMqOutageRecovery.ps1 -RequestCount 10
```

스크립트는 전용 Audit Queue를 만들고, RabbitMQ 중단·복구, Offset/Slot 스냅샷, DB/RabbitMQ 교차검증, 안전 복구까지 수행한다. 예외가 발생해도 `finally`에서 RabbitMQ와 Debezium을 다시 기동하고 이 실험이 시작한 Worker만 종료한다.

## 독립 교차검증

- DB 재조회: `READY_FOR_PLANNING` 10건
- DB 재조회: Candidate 1,200행, Generation 10개
- DB 재조회: 장소 중복 그룹 0, 순위 중복 그룹 0
- Audit 파일 재파싱: 메시지 10건, 고유 Generation ID 10개, `1216`~`1225` 정확히 일치
- RabbitMQ 스냅샷: publish/deliver/ACK `10/10/10`, redeliver 0, Main Queue 0
- 실행 종료 후 컨테이너: RabbitMQ healthy, Debezium UP

## 시각 증거 읽는 순서

1. `00-실험개요-장애범위-관측타임라인.png` — 질문과 한 문장 결론
2. `01-장애범위-RabbitMQ만중단.png` — API는 정상, RabbitMQ만 중단
3. `03-중단중-기대값-실제값.png` — 10건 보존과 전달 위치 정지
4. `04-Offset-Slot-전달위치보호.png` — 유실되지 않은 기술적 근거
5. `05-복구전달-발행10-전달10-ACK10.png` — 복구 뒤 10/10/10 일치
6. `07-최종판정-완료10-유실0-중복0.png` — 최종 PASS

`08-읽는순서-용어설명.png`은 기술 용어가 익숙하지 않은 독자를 위한 보조 이미지다.

## 증거 파일

- `result.json`: 최종 수치와 자동 판정
- `timeline.csv`: 실제 사건 시각
- `cdc-checkpoints.json`: Offset과 Replication Slot 비교
- `database-snapshots.json`: DB 상태 스냅샷
- `rabbitmq-snapshots.json`: Queue와 publish/deliver/ACK 수치
- `audit-messages.json`: 전용 Audit Queue가 받은 실제 메시지 10건
- `prometheus-evidence.json`: Grafana/Prometheus 관측 원본
- `debezium-broker-down.log`: Broker 중단 중 Sink 실패 증거
- `queries.sql`: DB 재검증용 SQL

## 해석할 때의 한계

- 이 실험에서 RabbitMQ 재전달 수는 0건이다. 따라서 **메시지 유실 방지와 backlog 복구**를 검증한 실험이며, 중복 메시지 멱등 처리는 실험 2와 3의 `재전달 → SKIP` 결과로 설명한다.
- Debezium 자체가 서비스 간 멱등성을 만들어 주는 것은 아니다. Debezium은 Outbox 이벤트 전달을 맡고, 중복 처리 방지는 Worker의 Generation 상태·Claim Version·DB 제약이 담당한다.
- 운영 DLQ의 기존 167건은 이전 실험 데이터다. 이 Run에서는 `167 → 167`로 증가하지 않았는지만 판정했다.
