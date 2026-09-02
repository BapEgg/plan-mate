# Collaboration Workspace WP-G 검증 기록

- 검증일: 2026-09-02
- 브랜치/기준 커밋: `codex/finish-trip-detail-demo` / `aed173a`
- 범위: WP-E 투표·적용, WP-F 전체/부분 재생성, WP-G 통합·반응형·접근성·세션 안정화
- 원칙: 자동 검증, 브라우저 확인, 실제 인프라 수동 검증을 구분하며 확인하지 않은 항목을 통과로 표시하지 않는다.

## 이번 검증에서 보완한 결함

1. 토큰 재발급은 성공했지만 재시도한 API가 다시 `401`을 반환하면 private token을 지우고 `SESSION_EXPIRED`를 한 번만 발행하도록 수정했다.
2. NARROW 화면에서 지도 pane만 선택했을 때 내부 지도가 고정 폭으로 남던 문제를 부모와 함께 `width: 100%`로 보정했다.
3. 지도에 불필요한 screen reader application mode를 강제하지 않도록 `region`으로 변경했다.
4. workspace pane switcher와 채팅/투표 tab에 Arrow, Home, End, roving focus를 적용했다.
5. 일정 DAY 선택기는 불완전한 tab 패턴 대신 `aria-pressed` toggle group으로 정리했다.
6. 일정 편집 dialog의 제목에 포커스된 상태에서 `Shift+Tab`을 누르면 dialog 밖으로 빠질 수 있던 focus trap을 수정했다.
7. 투표 마감, 동률, 멤버 제거 후 유권자 재계산, 중복 적용 방지를 service test로 고정했다.
8. 4일 일정이라는 이유만으로 서울 fixture가 거제도 generation에 제출되던 조건을 제거했다. fixture 안의 모든 placeId가 실제 후보 또는 현재 일정에 포함될 때만 선택한다.
9. 재생성 후보 수집에서 기존 일정 장소가 다시 수집되지 않아도, 이전 generation의 검증된 후보 snapshot 계보를 사용해 동일 validation을 통과할 수 있도록 보완했다. 후보 검증 자체는 우회하지 않는다.
10. fixture `505`를 사용하는 실제 revision E2E 뒤 current pointer가 최신 revision으로 이동하는 정상 동작을 마이그레이션 실패로 오인하지 않도록, 원본 보존과 revision 계보를 검증한다.

## 자동 검증 결과

| 영역 | 결과 | 근거 |
| --- | --- | --- |
| Backend 전체 회귀 | PASS | 459 tests, failure 0 |
| Frontend unit/component | PASS | 11 files, 54 tests |
| Frontend lint | PASS | ESLint exit 0 |
| Frontend production build | PASS | Vite build exit 0 |
| WP-E 투표 적용 | PASS | 통과 후 1회 적용, 동률 유지, 멤버 제거 재계산, 참여 부족 마감 |
| WP-F fixed anchor | PASS | 고정 장소 변경 거절, 30분 이내 허용, 범위 초과 거절 |
| Session refresh 실패 | PASS | refresh 실패와 refresh 뒤 재시도 `401` 모두 token 제거 및 만료 event 검증 |
| Route provider 오류 | PASS | Google/Kakao provider·network·key·quota 오류 매핑과 기존 route 보존 경계 검증 |
| Redis 오류 매핑 | PASS | refresh token store가 Redis failure를 서비스 예외로 변환 |
| RabbitMQ 정책 | PARTIAL | DLQ routing과 listener 예외 no-requeue는 검증, 실제 broker 중단·복구는 미실행 |
| Fixture production 격리 | PASS | profile과 feature flag가 함께 켜진 경우에만 fixture component 등록 |
| Fixture 목적지 호환성 | PASS | 2일 최소조건, 서울 4일, 거제 4일, 비호환 후보 미제출을 단위 테스트로 고정 |
| 재생성 기존 장소 검증 | PASS | 새 후보와 원본 generation 후보 snapshot을 합쳐 validation에 전달하고 강제방문 표시는 재사용하지 않음 |

실행 명령:

```powershell
backend\gradlew.bat test
node node_modules\eslint\bin\eslint.js .
node node_modules\vitest\vitest.mjs run
node node_modules\vite\bin\vite.js build
```

## 브라우저 확인 결과

| 화면/상태 | 결과 | 확인 내용 |
| --- | --- | --- |
| WIDE 1440×1000 | PASS | 일정·지도·채팅 3열, header와 선택 상태 유지 |
| MEDIUM 1024×900 | PASS | 일정+지도 2열과 하단 pane switcher |
| NARROW 390×844 | PASS | 일정/지도/대화 단일 pane 전환, 지도 폭 회귀 수정 |
| 720px layout pressure | PASS | `scrollWidth === clientWidth`, 수평 page overflow 없음 |
| Dialog keyboard | PASS | 제목 autofocus 뒤 `Shift+Tab`이 마지막 dialog action으로 순환 |
| Pane/tab keyboard | PASS | Arrow/Home/End 이동과 선택 tab focus 확인 |
| Console | PARTIAL | runtime error 없음. Google legacy `Marker` deprecation warning은 후속 기술부채 |

720px 검사는 1440px 화면의 200% 확대와 유사한 레이아웃 압력 확인이며 실제 브라우저 zoom 검사는 아니다.

## 실제 로컬 인프라 E2E 결과

대상은 PostgreSQL·Redis·RabbitMQ를 사용하는 trip `1530`과 실제 로컬 계정 `test`, `local1`, `local2`다.

| 시나리오 | 결과 | 확인 내용 |
| --- | --- | --- |
| 세 계정 접근 | PASS | OWNER 1명과 MEMBER 2명이 같은 trip에 접근 |
| 채팅 unread | PASS | `0 → 2 → 1 → 0`, 각 사용자의 읽음 처리 분리 |
| 채팅 멱등성 | PASS | 같은 `clientMessageId` 재전송 시 같은 message id 반환 |
| STOMP 채팅 전달 | PASS | OWNER와 MEMBER가 같은 `CHAT_MESSAGE_SENT` event를 실시간 수신 |
| 강퇴 실시간 차단 | PASS | OWNER의 강퇴 event 수신 뒤 대상 session 즉시 종료, 이후 event 미수신 |
| 강퇴 접근 차단 | PASS | 대상 사용자의 REST는 `404`, STOMP 재구독은 거부 |
| 제안·투표 | PASS | 유권자 3명, CHANGE 1 / KEEP 2로 `KEEP_CURRENT_WON`; current `505/v1` 유지 |
| MEMBER 직접 적용 | PASS | 권한 없는 apply가 `403`으로 차단 |
| OWNER 적용·복원 | PASS | `903/v2` 적용 뒤 역제안으로 `904/v3`; 원래 장소와 14:15 시간이 정확히 복원 |
| 진행 중 재생성 불변성 | PASS | `GENERATING`과 `READY_FOR_REVIEW` 동안 current는 `904/v3` 유지 |
| 거제 fixture 검증·저장 | PASS | generation `2512`, `2591` 모두 기존 validation과 저장을 거쳐 `COMPLETED`; 비교 4일 생성 |
| 재생성 거절 | PASS | 두 재생성을 `REJECTED` 처리한 뒤에도 current `904/v3` 유지 |
| V36 기존 DB 기동 | PASS | 로컬 PostgreSQL schema 36 검증 및 무변경 기동 |
| 자동 fixture subscriber | PARTIAL | 단위 테스트 PASS. 로컬 8080의 이전 PlanMate worker가 같은 RabbitMQ queue를 함께 소비해 새 generation의 AFTER_COMMIT event는 해당 프로세스에서 발생함 |

E2E 중 발견한 fixture 목적지 불일치와 기존 장소 후보 누락은 위 보완 사항 8~10으로 수정했다. 자동 subscriber의 단일 프로세스 실증은 기존 8080 worker를 중단하거나 전용 queue로 격리한 뒤 다시 수행해야 한다.

## 출시 전 남은 수동 gate

다음 항목은 로컬 단일 mock 화면이나 unit test만으로 실제 운영 조건을 증명할 수 없어 아직 PASS가 아니다.

1. 실제 계정의 채팅 REST/STOMP, unread, 투표, apply, 강퇴 후 열린 REST/STOMP 차단은 통과했다. 멤버십 복구는 로컬 initializer 재기동으로 확인했다.
2. Redis와 RabbitMQ process를 실제로 중단하고 재연결, 복구 안내, durable REST/DB snapshot 복원을 확인한다.
3. Kakao/Google provider를 실제 timeout/5xx 상태로 만들고 current 일정·marker·chat이 계속 사용 가능한지 확인한다.
4. 브라우저 실제 200% zoom, NVDA 등 실제 screen reader, 모바일 가상 키보드 환경을 확인한다.
5. 기존 로컬 DB의 V36 기동과 fixture/revision 계보는 통과했다. 빈 DB와 V21 시점 복원 DB에서 migration을 별도로 확인한다.
6. WP-G 통합 시나리오 중 남은 provider 장애와 접근성 실기기 gate를 끝낸 뒤 최종 완료로 닫는다.

## 현재 판정

코드 단위 회귀, production build, 핵심 반응형·keyboard, 실제 계정 REST/STOMP 협업과 강퇴 차단은 통과했다. WP-G는 **핵심 협업 E2E 완료, provider·인프라 장애 주입과 접근성 실기기 확인 대기** 상태다. 남은 수동 gate를 수행하기 전에는 release 완료로 표기하지 않는다.
