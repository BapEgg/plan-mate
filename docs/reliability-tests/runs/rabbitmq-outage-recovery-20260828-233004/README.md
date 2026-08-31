# RabbitMQ 중단 실험 Preflight 기록

- 실행 ID: `rabbitmq-outage-recovery-20260828-233004`
- 상태: **PRECHECK_FAILED**
- RabbitMQ 중단·자동 복구: 수행됨
- 생성된 테스트 Generation: 0건
- 원인: API의 `manual-handoff-enabled`를 false로 재기동해 Generation 생성 Controller가 비활성화됐다.
- 조치: API를 `manual-handoff=true`, `worker=false`로 재기동했다.

이 디렉터리는 최종 PASS/FAIL 표본으로 사용하지 않는다.
