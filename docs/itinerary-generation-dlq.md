# 일정 생성 Worker 실패와 DLQ 운영 정책

일정 생성 Worker는 하나의 claim 안에서 후보 수집을 처리한다. Google Places의 네트워크 오류, timeout, HTTP 429·5xx와 일시적인 DB 접근 실패는 retryable로 분류하며 `max-attempts`까지 재시도한다. Provider 요청 거부, API 설정 오류, generation invariant 위반, 잘못된 입력과 그 밖의 내부 처리 오류는 non-retryable로 분류하여 첫 실패에서 재시도를 중단한다.

현재 claim의 retryable 실패가 모든 attempt를 소진하거나 non-retryable 처리 실패가 발생하면 generation을 `FAILED`로 전환하고 예외를 listener 밖으로 전파한다. Listener의 `default-requeue-rejected=false` 설정과 main queue의 dead-letter exchange/routing key에 따라 메시지는 DLQ로 이동한다.

다음 경우에는 정상 반환하여 ACK하며 DLQ로 보내지 않는다.

- 이미 처리된 READY_FOR_PLANNING, COMPLETED, FAILED generation
- 유효 lease를 가진 중복 메시지
- reclaim 경쟁에서 이전 claim이 된 Worker의 성공 결과 또는 실패
- 그 밖에 claim을 얻지 못해 SKIP된 메시지

DLQ는 최종 실패 메시지를 보존해 운영자가 원인을 확인하는 격리 영역이다. 자동 main queue replay는 하지 않는다. `FAILED`는 terminal 상태이므로 단순 republish는 SKIP되고, 자동 상태 복구는 영구 장애에서 poison-message loop를 만들 수 있다.

## 재처리 절차

1. DLQ 메시지에서 `generationId`를 확인한다.
2. 해당 generation의 `failureReason`을 확인한다.
3. provider, configuration 또는 application 원인을 조사하고 해결한다.
4. 사용자가 정상 일정 생성 API를 다시 요청해 새 generation을 만든다.
5. 기존 FAILED generation은 이력으로 유지한다.

과거 input snapshot을 정확히 다시 실행해야 한다면 FAILED generation replay를 별도 기능으로 설계한다.
