# PlanMate 장애 주입 테스트 실행 기록

공식 완료 실험은 `result.json`과 최종 판정이 함께 있는 Run만 해당한다.

| 실험 | 최신 완료 Run | 판정 |
| --- | --- | :---: |
| Debezium 중단 후 재시작 | `runs/debezium-stop-restart-20260828-173040` | PASS |
| DB 반영 후 ACK 이전 Worker 강제 종료 | `runs/worker-before-ack-termination-20260828-211457` | PASS |
| CDC Offset 유실/불일치 및 과거 이벤트 재전달 | `runs/cdc-offset-mismatch-replay-20260828-220606` | PASS |
| Retryable/Non-Retryable 실패와 DLQ | `runs/retry-classification-dlq-20260828-223237` | PASS |
| Worker 장기 정지와 Stale Generation 복구 | `runs/stale-generation-recovery-20260828-225751` | PASS |
| RabbitMQ 중단 중 CDC 발행 실패와 복구 | `runs/rabbitmq-outage-recovery-20260828-233213` | PASS |
| RabbitMQ 전달 후 DB Claim 이전 Worker 강제 종료 | `runs/worker-before-claim-termination-20260829-002951` | PASS |

Preflight에서 중단됐거나 관측 순서를 고치는 과정에서 생성된 예비 Run은 공식 결과로 사용하지 않으며 저장소에도 포함하지 않는다. 위 표의 Run만 신뢰성 검증의 정량 근거로 사용한다.

공개 Run에는 결과 재검증에 필요한 구조화 데이터와 이미지를 보존한다. 로컬 계정·장비 정보가 포함된 PowerShell transcript(`commands.log`), PID, Debezium Offset 원본과 실행 중간 `runtime` 파일은 공개하지 않는다. Worker/Debezium 로그는 비밀값과 로컬 식별자를 검사한 공식 Run 파일만 보존한다.

각 실험의 재현 절차, 측정값과 PASS 근거는 해당 Run의 `README.md`와 `result.json`에서 확인할 수 있다.
