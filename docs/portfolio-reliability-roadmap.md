# PlanMate 포트폴리오 완성 및 장애 주입 테스트 로드맵

> 기준일: 2026-08-29  
> 현재 개발 저장소: <https://github.com/BapEgg/plan-mate>  
> 이전 작업 이력: <https://github.com/f-lab-edu/plan-mate>  
> 포트폴리오: <https://bapegg-dev.vercel.app/projects/planmate>  
> 개발 기록: <https://velog.io/@bapegg/posts>

## 1. 결론

현재 PlanMate는 포트폴리오 상세 페이지에 적힌 복구 구조와 자동 테스트까지는 대부분 구현되어 있다. 남은 핵심 작업은 기능을 처음부터 다시 만드는 것이 아니라 다음 세 가지다.

1. 실제 프로세스와 컨테이너를 중단할 수 있는 재현 가능한 Reliability Test 환경을 만든다.
2. 일곱 가지 장애를 실제로 주입하고, DB·Queue·Metric·Log 증거를 남긴다.
3. 결과와 설계 변경을 이 저장소, 기술 블로그, 포트폴리오에 같은 내용으로 반영한다.

상태를 혼동하지 않기 위해 아래 세 단계를 구분한다.

| 단계 | 의미 | 현재 상태 |
| --- | --- | --- |
| 구현 | 복구 정책과 코드가 존재한다 | 대부분 완료 |
| 자동 검증 | 단위·통합 테스트로 상태 전이와 트랜잭션을 검증한다 | 대부분 완료 |
| 실환경 검증 | 실제 Process Kill, Container Stop, Offset·Broker 장애로 검증한다 | 실험 1~7 PASS |

포트폴리오의 `PENDING`은 첫 번째와 두 번째 단계가 미완료라는 뜻이 아니라 세 번째 단계의 결과가 아직 없다는 뜻이다.

## 2. 확인된 현재 상태

### 저장소와 이력

- 로컬 `origin`은 `BapEgg/plan-mate`, `upstream`은 `f-lab-edu/plan-mate`다.
- 현재 저장소의 `main`과 `develop`은 같은 커밋을 가리킨다.
- 이전 저장소에는 총 42개의 PR이 남아 있다.
- 일정 생성 구조의 최신 근거는 이전 저장소의 [Issue #79](https://github.com/f-lab-edu/plan-mate/issues/79)와 [PR #80](https://github.com/f-lab-edu/plan-mate/pull/80)이다.
- PR #80에는 Snapshot, 후보 수집, Validation, Redelivery, Stale Recovery, Failure Classification, DLQ, Outbox Retention, Metric과 Grafana 작업이 함께 정리되어 있다.

### 구현되어 있는 신뢰성 경계

- Generation, Input Snapshot, Outbox Event를 한 트랜잭션에 저장한다.
- Debezium이 PostgreSQL Outbox를 읽어 RabbitMQ로 전달한다.
- Worker는 Claim Version과 Processing Lease를 사용한다.
- 현재 Claim을 잃은 Worker의 성공 결과와 실패 결과는 저장되지 않는다.
- Retryable/Non-Retryable 실패를 분류하고 최종 실패를 DLQ로 격리한다.
- Stale `COLLECTING_CANDIDATES` Generation을 찾아 복구 메시지를 발행한다.
- Outbox Retention과 Batch Cleanup이 구현되어 있다.
- Worker 결과·Retry·처리 시간·Generation 상태·Stale 수·후보 수·AI Validation 결과를 Metric으로 노출한다.
- Grafana의 `PlanMate Itinerary Monitoring` 대시보드가 위 Metric과 RabbitMQ Queue를 조회한다.

### 의도적으로 아직 하지 않은 범위

- OpenAI API 또는 Maps Grounding을 이용한 자동 일정 생성
- 운영 환경의 PlanMate 서비스 배포
- 실제 장애 주입 결과와 캡처 자료
- 채팅·투표·공동 편집

현재 기본 흐름은 실제 Google Places 후보를 수집한 뒤 ChatGPT에 Prompt를 수동 전달하고, 응답 JSON을 다시 제출하는 manual handoff다. 따라서 포트폴리오에서 `AI 일정 생성 서비스`라고 소개할 때는 자동 API 호출이 완료된 것처럼 표현하지 않는다.

### 2026-08-28 기준선 점검

- Frontend production build: 통과
- Docker Compose config 렌더링: 통과
- Backend test: 342개 중 269개 통과, 73개 실패
- Backend 실패 원인: 테스트 실행 시 PostgreSQL이 떠 있지 않아 Spring Context와 DB 통합 테스트가 연결에 실패함

이는 73개의 기능 회귀가 확인됐다는 뜻은 아니다. 다만 장애 주입 작업을 시작하기 전에 PostgreSQL·Redis·RabbitMQ를 실행한 상태로 전체 테스트를 다시 통과시켜야 한다.

## 3. 먼저 정리할 저장소 운영 방식

앞으로는 현재 저장소를 단일 기준 저장소로 사용한다.

- `origin`: 새 Issue, Branch, PR, Release가 생성되는 현재 저장소
- `upstream`: 이전 PR과 리뷰 근거를 읽기 위한 기록 저장소
- 기본 개발 방식: `main`에서 짧은 수명 브랜치를 만들고 `main`으로 PR
- 권장 브랜치 예: `infra/{issue}-reliability-runtime`, `test/{issue}-worker-redelivery`, `docs/{issue}-failure-result`
- 이전 저장소에는 새 PR을 만들지 않는다.

현재 저장소의 `main`과 `develop`이 같은 상태이므로 새 개인 저장소에서는 두 브랜치를 계속 운영하기보다 `main + short-lived branch` 방식이 이력 관리에 더 단순하다. `develop`을 유지하기로 결정한다면 모든 Reliability PR의 대상 브랜치를 `develop`으로 통일하고 마지막에 `develop -> main` Release PR을 만든다. 두 방식을 섞지 않는다.

## 4. 전체 작업 순서

### P0. 기준선과 공개 링크 정리

- [x] 현재 저장소를 `origin`으로 설정
- [x] 이전 저장소를 `upstream`으로 유지
- [x] 현재 구현 상태와 남은 작업 문서화
- [ ] 인프라 실행 후 Backend 전체 테스트 통과
- [ ] 포트폴리오 메인 카드의 GitHub 링크를 `BapEgg/plan-mate`로 변경
- [ ] PlanMate 상세 페이지의 `프로젝트 링크`를 `BapEgg/plan-mate`로 변경
- [ ] 상세 페이지에 `이전 멘토링 PR·Issue 이력` 링크를 별도로 추가
- [ ] 상세 페이지의 `배포 후 연결 예정` 문구가 서비스 미배포 상태와 일치하는지 유지 또는 갱신

### P1. Reliability Test 실행 환경

- [ ] API와 Worker를 서로 다른 Process 또는 Container로 실행
- [ ] API는 Worker Listener를 끄고, Worker는 HTTP 요청 처리 책임 없이 Consumer 역할만 수행
- [ ] Scheduler를 어느 Process에서 한 번만 실행할지 고정
- [ ] 장애 실험 전용 Compose override와 전용 Volume 이름 사용
- [ ] 실제 개인 개발 DB와 장애 실험 DB를 분리
- [ ] Google Places 결과에 따라 실험이 흔들리지 않도록 Reliability 전용 결정적 Candidate Provider 준비
- [ ] 정상·Retryable 실패·Non-Retryable 실패·장기 지연을 선택할 수 있는 테스트 전용 Fault Control 준비
- [ ] Test 전용 기능은 명시적 Profile/Property가 켜진 경우에만 활성화
- [ ] 민감한 API Key와 Token이 Log·스크린샷·문서에 노출되지 않도록 마스킹

Worker만 독립적으로 종료해야 HTTP 요청과 복구 Scheduler까지 함께 죽는 상황을 피할 수 있다. 현재 Compose에는 Backend가 포함되어 있지 않으므로, 실험 전에 `backend-api`와 `backend-worker`를 분리한 Reliability 전용 실행 구성이 필요하다.

### P2. 관측성과 증거 자동 수집

- [ ] 모든 Worker Log에 `generationId`, `tripId`, `claimVersion`, `redelivered`, `attempt`, `result`, `failureCode` 포함
- [ ] Retry Metric에 제한된 값의 `failureCode` 또는 `classification` Tag 추가 검토
- [ ] Recovery Publish와 Fenced Write 차단 횟수 Metric 추가 검토
- [ ] Generation 상태와 Claim 정보를 조회하는 읽기 전용 점검 Script 작성
- [ ] RabbitMQ ready/unacked/DLQ 수를 조회하는 Script 작성
- [ ] Prometheus 주요 Query 결과를 JSON 또는 Markdown으로 저장
- [ ] 실험 시작·주입·복구 시각을 UTC와 KST로 함께 기록
- [ ] 같은 명령을 다시 실행해도 다른 실험 자료를 덮어쓰지 않도록 Run ID 사용

권장 결과 디렉터리는 다음과 같다.

```text
docs/reliability-tests/
  README.md
  runs/
    2026-09-xx-debezium-restart/
      report.md
      before.json
      during.json
      after.json
      metrics.md
      logs/
      images/
```

원본 Log 전체를 Git에 넣기보다는 비밀 정보가 제거된 핵심 구간과 Query 결과만 보관한다. 큰 Grafana 이미지나 동영상은 GitHub Release 또는 별도 저장소를 사용하고 `report.md`에서 링크한다.

### P3. 일곱 가지 장애 주입 실험

아래 실험은 순서대로 진행한다. 앞 실험에서 발견된 구조 문제를 고친 뒤 다음 실험으로 넘어간다.

## 5. 실험 1 — Debezium 중단 후 재시작

### 검증 질문

Debezium이 멈춘 동안 DB에 Commit된 Outbox Event가 유실되지 않고, 재시작 후 RabbitMQ와 Worker까지 전달되는가?

### 사전 조건

- Backend API, Worker, PostgreSQL, RabbitMQ, Debezium, Prometheus, Grafana가 각각 식별 가능한 상태로 실행된다.
- 새로운 Trip/Generation을 만드는 Fixture가 준비되어 있다.
- 실험 동안 Outbox Retention으로 대상 Row가 삭제되지 않는다.

### 주입 절차

1. 정상 요청 한 건으로 전체 파이프라인이 동작하는지 확인한다.
2. Debezium Container만 중단한다.
3. 새 Generation을 생성하고 Generation과 Outbox Row가 Commit됐는지 확인한다.
4. RabbitMQ Main Queue와 Worker에 새 작업이 도착하지 않았음을 확인한다.
5. Debezium을 재시작한다.
6. 같은 Outbox Event가 전달되고 Generation이 `READY_FOR_PLANNING`으로 전환되는지 확인한다.

### 통과 기준

- Debezium 중단 중 Generation은 `CREATED`에 머물고 Outbox Row는 존재한다.
- Debezium 재시작 후 추가 사용자 요청 없이 처리가 이어진다.
- 최종 Candidate Snapshot 수가 1개 이상의 유효한 집합이다.
- Main Queue의 ready/unacked가 최종적으로 0이 된다.
- DLQ에 새 메시지가 생기지 않는다.
- 동일 Generation의 최종 후보 집합이 중복 저장되지 않는다.

### 필수 증거

- Debezium `up` Metric의 1 → 0 → 1 변화
- Outbox Row 생성 시각과 Generation 상태 변화 시각
- RabbitMQ Queue 변화
- Worker 성공 Metric 증가
- 실험 전·중·후 DB Query 결과

## 6. 실험 2 — DB 반영 후 ACK 이전 Worker 강제 종료

### 검증 질문

Worker가 Candidate와 `READY_FOR_PLANNING` 상태를 Commit했지만 RabbitMQ ACK 전 종료되면, 재전달된 메시지가 결과를 중복 생성하거나 상태를 훼손하지 않는가?

### 현재 추가로 필요한 장치

정상 실행에서 DB Commit과 Listener 반환 사이의 시간은 매우 짧아 사람의 수동 Process Kill만으로 정확한 지점을 재현하기 어렵다. Reliability Profile에서만 동작하는 `after-commit / before-listener-return` 대기 지점을 추가해야 한다.

### 주입 절차

1. Worker가 메시지를 받고 Candidate 저장 트랜잭션을 Commit한다.
2. Test Hook이 Listener 반환 직전에 Worker를 대기시킨다.
3. DB 상태가 `READY_FOR_PLANNING`임을 확인한다.
4. 해당 Worker Process 또는 Container만 강제 종료한다.
5. RabbitMQ가 Unacked 메시지를 다시 Queue에 넣는지 확인한다.
6. 두 번째 Worker 또는 재시작한 Worker가 Redelivery를 받도록 한다.

### 통과 기준

- 같은 `generationId`가 다시 전달된다.
- 재전달 메시지의 `redelivered`가 `true`로 기록된다.
- 이미 `READY_FOR_PLANNING`인 Generation은 새 Claim을 얻지 못하고 `SKIP`된다.
- Candidate Snapshot의 Place와 Rank Unique Constraint가 유지된다.
- Generation의 상태와 Claim Version이 불필요하게 변경되지 않는다.
- Queue는 비워지고 DLQ에는 들어가지 않는다.

현재 설정은 `acknowledge-mode`를 별도로 지정하지 않아 Spring AMQP의 기본 `AUTO` Mode를 사용한다. 이는 RabbitMQ의 `autoAck`가 아니라 Listener가 정상 반환하면 Spring Container가 ACK를 보내는 방식이다. 따라서 Listener 반환 전 Consumer Channel/Connection이 닫히면 아직 ACK되지 않은 메시지는 Requeue 대상이 된다. 실험 보고서에는 실행 당시 ACK Mode와 Container 설정을 함께 기록한다.

참고 문서:

- [Spring AMQP — Listener Container Acknowledge Mode](https://docs.spring.io/spring-amqp/reference/amqp/containerAttributes.html)
- [RabbitMQ — Consumer Acknowledgements and Automatic Requeueing](https://www.rabbitmq.com/docs/confirms)

## 7. 실험 3 — CDC Offset 유실/불일치와 과거 Event 재전달

### 용어 보정

`Offset 유실 = 반드시 과거 Event Replay`라고 단정하면 안 된다. PostgreSQL Debezium은 외부 Offset과 Replication Slot의 LSN을 함께 사용한다. Offset 파일만 잃었을 때 Snapshot이 발생할 수도 있고, Slot과 Offset이 불일치해 Connector가 실패할 수도 있다.

따라서 이 항목은 두 실험으로 나눈다.

### 3-A. Offset 유실/불일치 복구

검증 질문은 Offset Store가 사라지거나 Slot과 맞지 않을 때 Connector가 어떤 상태로 시작하며, 운영자가 이를 탐지하고 안전하게 복구할 수 있는가다.

- 반드시 폐기 가능한 전용 PostgreSQL·Debezium Volume에서만 실행한다.
- 정상 개발 Volume에 `down -v`, Volume 삭제, Offset 파일 삭제를 실행하지 않는다.
- Offset, Replication Slot의 `restart_lsn`·`confirmed_flush_lsn`, Outbox 보존 상태를 변경 전 기록한다.
- 실험 결과가 Replay인지 명시적 실패인지 관찰하고, 어느 경우든 조용한 데이터 유실로 끝나지 않는 것을 기준으로 삼는다.
- 필요한 경우 `snapshot.mode`와 `offset.mismatch.strategy`를 실험 환경에서 명시하고 결과에 남긴다.

### 3-B. 과거 Event 재전달 멱등성

Offset 복구 방식에 의존하지 않고, 이미 처리한 Outbox Payload를 같은 Exchange/Routing Key로 다시 Publish한다.

### 통과 기준

- 과거 Event가 다시 도착해도 `READY_FOR_PLANNING`, `COMPLETED`, `FAILED` 같은 Terminal/후속 상태가 이전 단계로 돌아가지 않는다.
- Candidate와 Itinerary가 중복 생성되지 않는다.
- 처리 결과는 `SKIP` 또는 Fencing 차단으로 관찰된다.
- Offset 불일치가 발생한 경우 Debezium Down/오류가 Metric 또는 Log로 탐지된다.
- 복구 절차와 데이터 유실 가능성이 보고서에 명시된다.

참고 문서:

- [Debezium PostgreSQL Connector — Replication Slot과 Offset](https://debezium.io/documentation/reference/3.5/connectors/postgresql.html)
- [Debezium — Offset Storage](https://debezium.io/documentation/reference/3.5/operations/debezium-platform.html)

포트폴리오 카드 제목도 `Debezium Offset 유실과 과거 Event Replay`보다 `CDC Offset 유실/불일치 및 과거 Event 재전달`로 바꾸는 편이 정확하다.

## 8. 실험 4 — Retryable / Non-Retryable 실패와 DLQ

**실행 상태:** **PASS** — `docs/reliability-tests/runs/retry-classification-dlq-20260828-223237/README.md`

### 검증 질문

일시 장애만 제한적으로 재시도하고, 복구 불가능한 오류는 첫 실패에서 중단한 뒤 최종 메시지를 DLQ에 격리하는가?

### 실제 사용한 실험 장치

- Property가 명시된 실험 Worker에서만 활성화되는 결정적 Failure Candidate Provider
- Retryable `PLACE_PROVIDER_UNAVAILABLE`과 Non-Retryable `PLACE_PROVIDER_REQUEST_REJECTED` 주입
- `generationId`, `tripId`, Attempt, 분류, 실패 코드를 남기는 구조화 Log와 Metric
- 기존 운영 DLQ 167건을 보존하는 실행별 전용 Exchange·Queue·DLQ

### Retryable 시나리오

- `PlaceProviderUnavailableException` 또는 `TransientDataAccessException`을 발생시킨다.
- `max-attempts=3`이면 최초 시도 포함 총 3번 처리되고 Retry Counter는 2 증가해야 한다.
- 세 번 모두 실패하면 현재 Claim이 Generation을 `FAILED`로 전환하고 메시지는 DLQ로 이동해야 한다.

### Non-Retryable 시나리오

- Provider Request Rejected, 잘못된 입력, Domain Invariant 위반 중 하나를 발생시킨다.
- 추가 Retry 없이 Generation을 `FAILED`로 전환하고 메시지를 DLQ로 이동해야 한다.

### 통과 기준

- Retryable과 Non-Retryable의 실제 Attempt 수가 정책과 일치한다.
- `failureReason`에는 예외 Stack Trace나 비밀 정보 대신 안정적인 Error Code만 저장된다.
- Main Queue는 비워지고 DLQ는 시나리오당 정확히 10건 증가한다.
- DLQ Message의 `generationId`로 DB 상태를 추적할 수 있다.
- 같은 FAILED Generation을 단순 Republish해 무한 재처리하지 않는다.

### 실제 판정값

- Retryable 10건: 실패 시도 30회, 추가 Retry 20회, FAILED 10건, DLQ 10건
- Non-Retryable 10건: 실패 시도 10회, 추가 Retry 0회, FAILED 10건, DLQ 누적 20건
- DLQ 고유 Generation ID 20개, `x-death` 20/20건, Candidate 0행, 운영 DLQ 변화 0건

## 9. 실험 5 — Worker 장기 정지와 Stale Generation 자동 복구

### 검증 질문

첫 Worker가 Claim을 잡은 채 멈추고 Lease가 만료되면 Recovery Scheduler가 작업을 다시 발행하며, 늦게 돌아온 첫 Worker의 결과는 Fencing되는가?

### 구현한 실험 장치

- Claim 직후 Candidate 저장 전에 Worker A 결과 반환을 120초 지연하는 Reliability Hook
- 실험용 Processing Lease 15초와 Recovery Scan Interval 10초
- Worker A와 Worker B를 8081/8082로 독립 실행하는 구성
- Recovery Scheduler를 Worker B에서만 실행하는 설정
- Claim 초기/복구, Recovery Publish, Fencing을 분리한 Metric과 구조화 로그

### 주입 절차

1. Worker A가 Claim Version 1을 획득한 뒤 대기한다.
2. Lease 만료까지 기다린다.
3. Recovery Scheduler가 복구 메시지를 발행한다.
4. Worker B가 Claim Version 2를 획득하고 정상 결과를 Commit한다.
5. Worker A의 대기를 해제해 Version 1 결과를 늦게 저장하도록 시도한다.

### 통과 기준

- Recovery Batch 범위 안에서 메시지가 다시 발행된다.
- Claim Version이 1에서 2로 증가한다.
- Worker B 결과만 Candidate Snapshot과 상태에 반영된다.
- Worker A의 성공 결과와 실패 결과 모두 현재 상태를 변경하지 못한다.
- 최종 상태는 `READY_FOR_PLANNING`이고 Candidate 집합은 하나다.
- Recovery Publish와 Fenced Write 차단이 Log 또는 Metric으로 확인된다.

### 실제 판정값

- 공식 Run: `stale-generation-recovery-20260828-225751`
- Worker A 초기 Claim Version 1 10건, Queue Unacked 10건, Lease 만료 10건
- Recovery Publish 10건, Worker B Claim Version 2 10건, 성공 10건
- Worker A 늦은 결과 Fencing 10건, SKIP 10건
- 최종 READY 10/10건, FAILED 0건, Candidate 1,200행, 중복 0행
- RabbitMQ Publish/Deliver/ACK 20/20/20회, 실험 DLQ 0건, 운영 DLQ 변화 0건
- Lease 만료 관측 후 READY 10/10 완료 13.688초
- 최종 판정: **PASS**

## 10. 실험 6 — RabbitMQ 중단 중 CDC 발행 실패와 복구

### 검증 질문

RabbitMQ가 중단돼 Debezium이 Outbox 이벤트를 발행하지 못할 때 처리 위치를 잘못 확정하지 않고, Broker 복구 후 10건을 모두 전달하는가?

### 구현한 실험 장치

- RabbitMQ만 중단하고 API·PostgreSQL·Grafana는 유지하는 PowerShell 실행기
- 실행별 전용 Audit Queue와 Generation ID 비교
- Debezium Offset 파일 SHA-256과 PostgreSQL Replication Slot 전·중·후 스냅샷
- RabbitMQ publish/deliver/ACK와 DB READY/Candidate 교차검증
- 예외 시 RabbitMQ·Debezium을 복구하고 실험 Worker만 종료하는 안전 정리

### 통과 기준

- 중단 중 Outbox 10건과 전달 대기 10건이 존재한다.
- Debezium Offset과 Replication Slot 확정 위치가 중단 중 움직이지 않는다.
- RabbitMQ 복구 후 publish/deliver/ACK가 10/10/10으로 일치한다.
- Audit Queue의 고유 Generation ID 10개가 DB 대상 ID와 정확히 일치한다.
- 최종 READY 10/10, Candidate 1,200행, 유실·중복·DLQ 증가가 0이다.

### 실제 판정값

- 공식 Run: `rabbitmq-outage-recovery-20260828-233213`
- RabbitMQ 중단 28.541초, Sink 실패 로그 54회
- 중단 전/중 Offset SHA 동일, Slot 확인 위치 `0/2C8EF68` 동일
- 복구 후 publish/deliver/ACK 10/10/10, Audit 10건
- READY 10/10, Candidate 1,200행, 중복 0, 유실 0, 운영 DLQ 167→167
- RabbitMQ Health 복구 5.416초, Start→READY 10/10 15.905초
- 최종 판정: **PASS**

이 실험은 메시지 유실 방지와 backlog 복구를 검증한다. RabbitMQ 재전달은 0건이므로 중복 메시지 멱등성 근거는 실험 2와 3의 재전달·SKIP 결과를 함께 사용한다.

## 11. 실험 7 — RabbitMQ 전달 후 DB Claim 이전 Worker 강제 종료

### 검증 질문

RabbitMQ가 메시지를 Worker에 전달했지만 DB claim을 얻기 전에 Worker가 종료되면, ACK되지 않은 메시지가 Queue로 돌아가 다른 Worker에서 유실 없이 처리되는가?

### 구현한 실험 장치

- Listener의 delivery Metric 기록과 Worker Service의 DB claim 호출 사이에 기본값 0초인 일회성 지연 훅
- Worker A prefetch 1과 claim 전 지연 120초
- 종료 직전 delivery·claim Metric, DB 상태, RabbitMQ ready/unacked 교차검증
- Worker A 강제 종료 후 재큐잉과 Prometheus DOWN 확인
- 지연 없는 Worker B 재기동 후 redelivery·initial claim·success·SKIP 측정

### 통과 기준

- 종료 직전 Queue ready/unacked가 9/1이고 DB 10건이 모두 CREATED다.
- 종료 직전 claim, COLLECTING, READY, Candidate가 모두 0이다.
- Worker A 종료 후 Queue ready/unacked가 10/0이 된다.
- Worker B에서 redelivery 1, initial claim 10, success 10, SKIP 0이 관측된다.
- 최종 READY 10/10, Candidate 1,200행, 유실·중복·DLQ 증가가 0이다.

### 실제 판정값

- 공식 Run: `worker-before-claim-termination-20260829-002951`
- 종료 직전 delivery 1, Queue ready/unacked 9/1, CREATED 10, claim·Candidate 0
- 종료 후 Queue ready/unacked 10/0, 재큐잉 감지 4.563초
- Worker B redelivery 1, initial claim 10, success 10, SKIP 0
- publish/deliver/ACK/redelivery 10/11/10/1
- READY 10/10, Candidate 1,200행, 중복 0, 유실 0, 운영 DLQ 167→167
- Worker Health 복구 11.955초, 전체 완료 14.074초
- 자동 검사 20/20 통과, 최종 판정: **PASS**

Debezium은 DB 이벤트를 RabbitMQ까지 전달한다. 이 실험의 재전달 안전성은 Debezium 단독 기능이 아니라 RabbitMQ ACK·재전달과 Worker의 DB 상태·제약이 함께 만든 결과다.

## 12. 실험 보고서 공통 양식

각 `report.md`는 다음 내용을 동일한 순서로 기록한다.

```markdown
# 실험 제목

## 가설
## 대상 Commit / 환경
## 변경하지 않는 변수
## 장애 주입 지점
## 예상 상태 전이
## 실행 절차
## 실제 Timeline
## DB 결과
## RabbitMQ 결과
## Prometheus / Grafana 결과
## Log 핵심 구간
## 통과 기준별 판정
## 예상과 달랐던 점
## 설계 또는 코드 변경
## 재실행 결과
## 포트폴리오에 사용할 요약
```

보고서의 판정은 `PASS`, `FAIL`, `INCONCLUSIVE` 중 하나만 사용한다. 실패한 실험도 숨기지 않고 원인, 수정 PR, 재실행 결과를 연결한다. 이 과정이 포트폴리오에서는 단순 성공 화면보다 더 강한 문제 해결 근거가 된다.

## 13. 권장 GitHub Issue 분리

| 순서 | Issue | 완료 조건 |
| --- | --- | --- |
| 1 | `[docs] 저장소 이전 및 포트폴리오 링크 정리` | README와 공개 링크가 새 저장소를 가리킴 |
| 2 | `[infra] Reliability 전용 API/Worker 분리 실행 환경` | API 중단 없이 Worker만 Kill 가능 |
| 3 | `[test] 결정적 Fault Control과 증거 수집 도구` | 성공·지연·두 실패 유형을 반복 재현 가능 |
| 4 | `[test] Debezium 중단·재시작 장애 주입` | 실험 1 보고서 PASS |
| 5 | `[test] ACK 이전 Worker 종료와 Redelivery 검증` | 실험 2 보고서 PASS |
| 6 | `[test] CDC Offset 불일치와 Event 재전달 검증` | 실험 3-A/3-B 결과 기록 |
| 7 | `[test] Retry 분류와 DLQ 전환 검증` | 실험 4 보고서 PASS |
| 8 | `[test] Stale Worker Recovery와 Fencing 검증` | 실험 5 보고서 PASS |
| 9 | `[test] RabbitMQ 중단과 CDC backlog 복구 검증` | 실험 6 보고서 PASS |
| 10 | `[docs] Reliability Test 결과 공개` | 저장소·Velog·포트폴리오 내용 일치 |

하나의 거대한 장애 주입 브랜치보다 위 Issue별 PR을 남기는 것이 설계 변경의 원인과 검증 결과를 추적하기 쉽다.

## 14. Markdown 외 관리 방법

이 문서를 없애고 GitHub Issue만 사용하는 것보다 다음 혼합 방식이 적합하다.

- 이 문서: 전체 범위, 실험 정의, 공통 판정 기준의 단일 기준 문서
- GitHub Issue: 실제 작업 단위, 체크리스트, PR 연결
- GitHub Project: `Backlog -> Ready -> Running -> Evidence Review -> Done` 상태 시각화
- `docs/reliability-tests/runs`: 실행 결과와 설계 변경 근거
- Velog: 한 실험당 문제·가설·실행·결과·회고 중심의 글
- 포트폴리오: 최종 판정과 핵심 Metric, 설계 변경만 간결하게 표시

즉, Markdown은 계획과 결과의 원본으로 유지하고 GitHub Issue/Project는 진행 상태를 관리하는 용도로 사용한다.

## 15. 포트폴리오 반영 완료 기준

다음 조건을 모두 만족한 경우에만 장애 주입 카드를 `실험 완료`로 바꾼다.

- 대상 Commit SHA가 기록되어 있다.
- 재현 절차가 다른 환경에서도 실행 가능하다.
- 장애 주입 전·중·후 Timeline이 있다.
- DB, Queue, Metric 중 최소 두 종류 이상의 객관적 증거가 있다.
- 통과 기준별 판정이 있다.
- 실패 발견 시 수정 PR과 재실행 결과가 연결되어 있다.
- 공개 자료에서 Token, API Key, Email, 내부 경로가 제거되어 있다.
- 저장소 보고서, Velog, 포트폴리오의 표현이 서로 모순되지 않는다.

### 포트폴리오에 유지해도 되는 표현

- 복구 로직과 자동 테스트 구현 완료
- Transactional Outbox와 Debezium CDC 적용
- Claim/Lease/Fencing 기반 Stale Worker 결과 차단
- Retryable/Non-Retryable 분류와 DLQ 정책 구현

### 검증 범위를 넘어 사용하면 안 되는 표현

- 모든 장애 유형과 모든 트래픽 규모에서 이벤트 유실이 없음을 보장했다
- Debezium Offset 유실 후 별도 검증 없이 모든 Event가 자동 복구된다
- 로컬 복구 시간이 운영 환경의 최대 복구 시간을 보장한다
- 운영 환경에서도 동일하게 동작함을 확인했다

## 16. 다음 작업

장애 주입 실험 1~7은 모두 완료됐다. 다음 작업은 각 공식 Run과 이미지 경로를 현재 GitHub 저장소에 공개하고, 포트폴리오와 기술 블로그의 숫자·용어가 이 문서와 일치하는지 검수하는 것이다. 코드 변경은 실험별 Issue와 PR로 나눠 구현 배경, 실패한 Preflight, 최종 PASS Run을 추적 가능하게 연결한다.
