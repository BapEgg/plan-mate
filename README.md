# PlanMate

사용자 조건과 실제 장소 후보를 바탕으로 검증 가능한 여행 일정을 만드는 개인 프로젝트입니다.

현재 일정 생성 경로는 Transactional Outbox, Debezium CDC, RabbitMQ Worker로 분리되어 있으며, 중복 전달과 Worker 중단을 고려한 Claim/Lease/Fencing, Retry/DLQ, Prometheus/Grafana 관측 구조를 포함합니다. AI 일정 생성은 서버가 외부 AI API를 직접 호출하지 않는 manual handoff 방식입니다.

## 문서

- [포트폴리오 완성 및 장애 주입 테스트 로드맵](docs/portfolio-reliability-roadmap.md)
- [장애 주입 테스트 공식 Run 목록](docs/reliability-tests/README.md)
- [Debezium 중단 후 재시작 장애 주입 테스트 결과](docs/reliability-tests/runs/debezium-stop-restart-20260828-173040/README.md)
- [ACK 이전 Worker 강제 종료 장애 주입 테스트 결과](docs/reliability-tests/runs/worker-before-ack-termination-20260828-211457/README.md)
- [CDC Offset 불일치와 과거 이벤트 재전달 장애 주입 테스트 결과](docs/reliability-tests/runs/cdc-offset-mismatch-replay-20260828-220606/README.md)
- [Retry 분류와 DLQ 격리 장애 주입 테스트 결과](docs/reliability-tests/runs/retry-classification-dlq-20260828-223237/README.md)
- [Stale Generation 자동 복구와 Fencing 장애 주입 테스트 결과](docs/reliability-tests/runs/stale-generation-recovery-20260828-225751/README.md)
- [RabbitMQ 중단 중 CDC 발행 실패와 복구 장애 주입 테스트 결과](docs/reliability-tests/runs/rabbitmq-outage-recovery-20260828-233213/README.md)
- [RabbitMQ 전달 후 Claim 이전 Worker 강제 종료 장애 주입 테스트 결과](docs/reliability-tests/runs/worker-before-claim-termination-20260829-002951/README.md)
- [장애 주입 테스트 결과 보고 및 포트폴리오 반영 가이드](docs/장애주입테스트-결과보고-포트폴리오-반영가이드.md)
- [로컬 인프라 실행 방법](infra/README.md)
- [일정 생성 Worker 실패와 DLQ 운영 정책](docs/itinerary-generation-dlq.md)
- [수동 AI 일정 생성 검증 방법](docs/manual-itinerary-verification.md)

## 저장소

- 현재 개발 저장소: <https://github.com/BapEgg/plan-mate>
- 2026년 F-Lab 멘토링 기간의 PR·Issue 이력: <https://github.com/f-lab-edu/plan-mate>

새 작업은 현재 개발 저장소의 Issue와 짧은 수명 브랜치에서 진행합니다. 이전 저장소는 구현 배경과 리뷰 이력을 확인하는 기록 보관소로 사용합니다.
