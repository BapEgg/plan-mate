# RabbitMQ 중단 실험 Preflight 기록

- 실행 ID: `rabbitmq-outage-recovery-20260828-232849`
- 상태: **PRECHECK_FAILED**
- 장애 주입: 실행하지 않음
- 생성된 테스트 Generation: 0건
- 원인: 이전 실험에서 남은 API 프로세스가 Health 요청에 응답하지 않았다.
- 조치: 포트 8080의 PlanMate API만 Worker 비활성화 설정으로 재기동했다.

이 디렉터리는 최종 PASS/FAIL 표본으로 사용하지 않는다.
