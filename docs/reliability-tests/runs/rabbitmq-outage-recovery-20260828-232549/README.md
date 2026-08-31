# RabbitMQ 중단 실험 검증 제외 기록

- 실행 ID: `rabbitmq-outage-recovery-20260828-232549`
- 테스트 요청: `장애테스트-래빗엠큐중단-01`~`10`
- 장애 시간: 87.185초
- 상태: **SUPERSEDED — 공식 PASS/FAIL 표본에서 제외**

이 Run은 핵심 데이터 결과가 Outbox 10, publish/deliver/ACK 10/10/10, READY 10/10, 유실 0으로 정상이다. 다만 자동 판정기가 **Outbox 이벤트를 만들기 전에** Debezium 장애 상태를 먼저 확인해 `debeziumSinkReportedDown=false`를 기록했다. 장애 현상 자체의 실패가 아니라 관측 순서 결함이다.

조치: 10건의 Outbox가 생성된 다음 Debezium Sink 장애를 확인하도록 실행 순서를 수정했다. 공식 결과는 `rabbitmq-outage-recovery-20260828-233213`만 사용한다.

| 항목 | 실제값 |
| --- | ---: |
| 중단 중 Outbox | 10건 |
| 중단 중 전달 대기 | 10건 |
| Audit Queue 이벤트 | 10건 |
| RabbitMQ publish/deliver/ACK | 10 / 10 / 10 |
| READY | 10/10 |
| Candidate / 중복 | 1200 / 0 |
| 이벤트 유실 |  |
| DLQ 증가 |  |
| RabbitMQ Health 복구 | 7.272초 |
| RabbitMQ 복구 후 Debezium UP | 8.28초 |
| RabbitMQ Start 후 READY 10/10 | 15.706초 |

자동 판정에서 제외된 검사: `debeziumSinkReportedDown`
