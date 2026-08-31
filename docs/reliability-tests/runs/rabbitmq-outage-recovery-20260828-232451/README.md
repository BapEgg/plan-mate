# RabbitMQ 중단 실험 Preflight 기록

- 실행 ID: `rabbitmq-outage-recovery-20260828-232451`
- 상태: **PRECHECK_FAILED**
- 장애 주입: 실행하지 않음
- 생성된 테스트 여행: 0건
- 원인: 실험 5 종료 후 Main Queue에 연결된 Worker Consumer가 0명이었다.
- 조치: 실험 6 실행기가 전용 Worker를 직접 기동하고 종료하도록 수정했다.

이 디렉터리는 최종 PASS/FAIL 표본으로 사용하지 않는다.
