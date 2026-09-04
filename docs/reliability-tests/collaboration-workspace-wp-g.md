# Collaboration Workspace WP-G 검증 기록

- 검증일: 2026-09-02
- 브랜치/기준 커밋: `codex/finish-trip-detail-demo` / `12b4760` 이후 작업 트리
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
11. 경로 재조회 실패 시 frontend가 현재 route를 지우던 동작을 수정했다. 같은 itinerary id/version/DAY의 마지막 검증 route만 유지하고, 지도에는 기존 geometry를, 일정에는 기존 이동시간을 `이전 확인`으로 표시한다.
12. Kakao Directions base URL을 환경 설정으로 주입할 수 있게 하되 production 기본 URL은 유지했다. 로컬 fault server로 503과 read timeout을 결정적으로 재현할 수 있다.
13. Redis process 중단 시 인증 요청이 기본 client timeout에 기대어 약 60초간 대기하던 문제를 발견했다. 연결 timeout 1초, command timeout 2초를 명시해 refresh·login이 약 2초 안에 `REFRESH_TOKEN_STORE_UNAVAILABLE`로 종료되게 했다.
14. RabbitMQ data 경로를 compose의 명시적 named volume으로 고정했다. 이미 실행 중인 anonymous volume container는 자동 교체하지 않았으며, 새 로컬 환경 또는 의도적으로 재생성하는 시점부터 `rabbitmq-data`를 사용한다.
15. Google Places endpoint와 connect/read timeout을 환경 설정으로 분리해 503·timeout을 결정적으로 재현할 수 있게 했다. 목록 조회의 첫 provider 장애 뒤 추가 호출을 중단해 장소 수만큼 timeout이 누적되지 않는다.
16. Google Places가 일시적으로 실패해도 같은 trip의 최신 generation candidate snapshot에서 장소명·좌표를 복구해 일정·marker·DAY route를 유지한다. frontend는 이를 `저장된 장소 정보`로 명시하고 Google Maps 외부 링크는 숨긴다.
17. 데스크톱 지도 상세 패널에서 외부 지도 링크 문구가 약 10px 잘리던 문제를 `Google 지도에서 보기`로 정리해 좁은 패널에서도 읽히도록 했다.
18. 여행방 관리 drawer를 닫은 뒤 키보드 포커스가 사라지던 문제를 열기 버튼으로 복귀하도록 수정하고 component test로 고정했다.
19. 관리 drawer의 이메일·방장 이전 form에 `name`, `autocomplete`, 맞춤법 검사 설정을 보완하고, 내부 스크롤이 페이지로 전파되지 않도록 overscroll 경계를 추가했다.
20. NARROW 화면에서 참여자 목록과 방 관리 진입점이 함께 숨겨지던 문제를 발견해, 첫 참여자 avatar를 쓰는 36px 요약 버튼으로 복원했다. 목록 popover는 trigger 아래에 열려 같은 버튼으로 닫을 수 있다.
21. 채팅 메시지 목록 전체(`.trip-chat-preview`)에 걸려있던 `aria-live="polite"`를 제거했다. 이 상태로는 초기 history 로드, pagination, 반응 토글마다 스크린리더가 대화 전체를 다시 낭독할 위험이 있었다. 다른 사용자의 새 메시지가 도착했을 때만 갱신되는 숨김 announcer(`sr-only`)로 대체했다 — 본인이 보낸 메시지의 echo와 초기 로드는 announcer를 갱신하지 않는다.
22. 모바일 가상 키보드로 뷰포트 높이가 줄어들면 채팅 입력창이 `.trip-chat-panel`의 `overflow: hidden`에 잘려 하단 pane switcher와 겹치거나 아예 보이지 않던 문제를 수정했다. 항상 렌더링되던 빈 타이핑 표시줄을 `:empty`일 때 접고, 입력창·검색 도구줄을 `flex-shrink: 0`으로 보호해 메시지 목록만 줄어들게 했으며, `max-height: 520px`에서 메시지 목록·입력창 padding을 추가로 줄였다.

## 자동 검증 결과

| 영역 | 결과 | 근거 |
| --- | --- | --- |
| Backend 전체 회귀 | PASS | 462 tests, failure 0 |
| Frontend unit/component | PASS | 15 files, 61 tests |
| Frontend lint | PASS | ESLint exit 0 |
| Frontend production build | PASS | Vite build exit 0 |
| WP-E 투표 적용 | PASS | 통과 후 1회 적용, 동률 유지, 멤버 제거 재계산, 참여 부족 마감 |
| WP-F fixed anchor | PASS | 고정 장소 변경 거절, 30분 이내 허용, 범위 초과 거절 |
| Session refresh 실패 | PASS | refresh 실패와 refresh 뒤 재시도 `401` 모두 token 제거 및 만료 event 검증 |
| Route provider 오류 | PASS | Kakao 503→503, read timeout→504 실제 장애 주입과 동일 revision/DAY의 기존 route 보존 경계 검증 |
| Redis 장애 경계·fail-fast | PASS | 실제 process 중단에서 chat REST는 유지되고 refresh·login은 503으로 약 2초 안에 종료, 복구 뒤 기존 refresh token 재사용 확인 |
| RabbitMQ process 복구 | PASS | 실제 broker 중단 중 trip/chat/presence REST와 STOMP 유지, 재기동 뒤 itinerary consumer 자동 재연결 확인 |
| RabbitMQ 대기 메시지 전달 | PASS | 공식 실험 6에서 broker 중단 중 Outbox/CREATED 10건 보존, 복구 뒤 publish/deliver/ACK 10/10/10과 READY 10/10, 후보 중복 0, 유실 0 검증 |
| Fixture production 격리 | PASS | profile과 feature flag가 함께 켜진 경우에만 fixture component 등록 |
| Fixture 목적지 호환성 | PASS | 2일 최소조건, 서울 4일, 거제 4일, 비호환 후보 미제출을 단위 테스트로 고정 |
| 재생성 기존 장소 검증 | PASS | 새 후보와 원본 generation 후보 snapshot을 합쳐 validation에 전달하고 강제방문 표시는 재사용하지 않음 |
| Google Places 장애 fallback | PASS | 첫 provider 장애 뒤 호출 중단, 같은 trip 최신 후보 snapshot의 이름·좌표 사용, source 계약과 frontend 부분 실패 안내 검증 |

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
| NARROW 390×500 viewport pressure | PASS | 가상 키보드로 높이가 줄어든 상황을 모사해 채팅 입력창과 pane switcher가 viewport 안에 유지됨 |
| 여행방 관리 WIDE/NARROW | PASS | 1440×1000 drawer와 390×844 full-width drawer에서 잘림·수평 overflow 없음 |
| 여행방 관리 keyboard | PASS | dialog autofocus, 닫기 뒤 `여행방 관리` trigger로 focus 복귀, form metadata 확인 |
| Google Places 저장 정보 안내 | PASS | 인증된 trip `1530`에서 실제 장소명·marker·일정은 유지되고 저장 정보 안내와 외부 링크 숨김이 일관되게 표시됨 |
| Dialog keyboard | PASS | 제목 autofocus 뒤 `Shift+Tab`이 마지막 dialog action으로 순환 |
| Pane/tab keyboard | PASS | Arrow/Home/End 이동과 선택 tab focus 확인 |
| Console | PARTIAL | runtime error 없음. Google legacy `Marker` deprecation warning은 후속 기술부채 |
| 200% zoom 근사(1440→720 유효 폭) | PASS | CSS `zoom`은 `window.innerWidth`를 바꾸지 않아 실제 zoom과 다르다는 점을 확인한 뒤, 유효 폭 축소(720px)로 재검증. 기존 1180px/780px `flex-wrap` fallback이 정상 동작해 헤더 제목 squeeze 없음 |
| 접근성 트리 감사(스크린리더 대체) | PASS→일부 FAIL 발견·수정 | 실계정 trip `1530`에서 heading 계층(H1→H2, 스킵 없음), `<details>/<summary>` 참여자 popover 정상 확인. 채팅 메시지 목록 전체가 `aria-live="polite"`인 결함(보완 21) 발견·수정 |
| NARROW 375×450/400 가상 키보드 압력(심화) | 450: PASS(수정 후) / 400: PARTIAL | 기존 390×500보다 얕은 키보드를 모사. 450px에서는 채팅 입력창이 pane switcher·패널 clip 모두로부터 자유로워짐(보완 22). 400px는 남은 chrome(검색 도구줄+입력창)만으로도 여유 공간을 초과해 입력창이 몇 px 잘리는 residual 존재 — 실기기에서 흔치 않은 극단값으로 판단해 이번 범위에서는 남겨둠 |

720px 검사는 1440px 화면의 200% 확대와 유사한 레이아웃 압력 확인이며 실제 브라우저 zoom 검사는 아니다. 390×500 검사는 viewport 높이 축소로 가상 키보드 압력을 모사한 것으로 실제 모바일 OS 키보드 검사는 아니다. 접근성 트리 감사와 위 두 심화 검사는 모두 이 도구 환경에서 가능한 범위의 근사(CSS `zoom`/유효 폭 축소, 접근성 트리 판독, 뷰포트 높이 축소)이며 실제 브라우저 pinch/ctrl+zoom, 실제 NVDA 음성 출력, 실제 모바일 OS 키보드를 대체하지 않는다. 두 결함 수정 모두 기존 61개 frontend 테스트는 통과하지만, 회귀를 직접 고정하는 신규 자동 테스트는 아직 없다.

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
| 빈 DB V1→V36 | PASS | 격리 DB에서 Flyway 36개 적용, 실패 0, product table 29개, 애플리케이션 health `UP` |
| V21 fixture V22→V36 | PASS | 합성 trip `1530`을 V21에 적재한 뒤 15개 migration 적용. current `505`, timezone `Asia/Seoul`, ACTIVE 멤버 3명·OWNER 1명, 4일·23개 item과 일차별 `5/5/7/6` 보존, 애플리케이션 health `UP` |
| 자동 fixture subscriber | PARTIAL | 단위 테스트 PASS. 로컬 8080의 이전 PlanMate worker가 같은 RabbitMQ queue를 함께 소비해 새 generation의 AFTER_COMMIT event는 해당 프로세스에서 발생함 |
| Kakao provider 장애 격리 | PASS | 별도 18080 서버에서 정상 route 200 뒤 mock 503·timeout 주입. 각각 `ROUTE_PROVIDER_UNAVAILABLE` 503, `ROUTE_PROVIDER_TIMEOUT` 504; current `904/v3`, DAY 1 marker 5곳, chat REST 조회 유지 |
| Redis process 중단·복구 | PASS | 별도 18080 서버에서 chat REST `200`; refresh/login은 각각 `503`과 `REFRESH_TOKEN_STORE_UNAVAILABLE`, 2039ms/2098ms에 종료; Redis healthy 뒤 기존 refresh token `200`, chat `200` |
| RabbitMQ process 중단·복구 | PASS | 중단 중 trip/chat/presence REST `200`, STOMP subscribe 성공; 재기동 뒤 queue consumer `2 → 2`, chat/presence `200`, Debezium health `UP` |
| RabbitMQ CDC backlog 복구 | PASS | 공식 run `rabbitmq-outage-recovery-20260828-233213`: 중단 중 Outbox/CREATED 10건, 복구 뒤 publish/deliver/ACK 10/10/10, READY 10/10, candidate 1,200, 유실·중복·DLQ 증가 0 |
| Google Places 503 격리 | PASS | 별도 18080 서버와 mock 503에서 trip `1530` DAY 1의 장소 5/5를 `SAVED_SNAPSHOT`으로 복구, 좌표 5/5·route `READY` 4구간·chat 15건 유지. place view와 route가 각각 첫 실패 1회만 provider 호출 |

E2E 중 발견한 fixture 목적지 불일치와 기존 장소 후보 누락은 위 보완 사항 8~10으로 수정했다. 자동 subscriber의 단일 프로세스 실증은 기존 8080 worker를 중단하거나 전용 queue로 격리한 뒤 다시 수행해야 한다. Redis·RabbitMQ process 중단 테스트는 `docker stop` 대상을 정확히 지정하고 `finally`에서 재기동·health 확인했으며, 사용자 8080 서버는 중단하지 않았다.

RabbitMQ backlog 판정은 이미 저장소에 보존된 [공식 실험 6](runs/rabbitmq-outage-recovery-20260828-233213/README.md)과 `result.json` 원본을 사용했다. 해당 run은 21개 자동 check가 모두 true이고, 현재 DB 재조회에서도 generation `1216`~`1225`가 READY 10/10, candidate 1,200행, `(generation, place)`·`(generation, rank)` 고유값 각각 1,200개로 유지됐다. 공식 run이 커밋된 `c4d35e0` 이후 일정 generation listener·worker·Debezium·실험 스크립트에는 변경이 없고, Outbox 변경은 기본 비활성 realtime reconciliation용 보조 컬럼뿐이다. 현재 8080 worker를 중단하지 않고 같은 파괴적 실험을 중복 실행할 이유가 없어 재실행 대신 보존 증거와 현재 DB를 교차검증했다.

마이그레이션 검증은 사용자 `planmate` DB와 8080 서버를 건드리지 않고 별도 임시 DB에서 수행했다. 빈 DB는 V1부터 V36까지 순차 적용했고, V21 복원 검증은 [합성 V21 fixture](fixtures/v21-migration-baseline.sql)를 적재한 뒤 V22부터 V36까지 올렸다. V22의 멤버 생명주기 backfill은 `joined_at = created_at`, `left_at IS NULL`, ACTIVE OWNER 1명으로 확인했고, V23~V36의 current 일정 포인터·시간대·revision 및 신규 collaboration table 13개도 확인했다. 검증을 마친 임시 DB는 제거한다.

## 출시 전 남은 수동 gate

실제 계정 협업, Redis·RabbitMQ 복구, Kakao·Google provider 장애 격리, 기존·빈·V21 복원 DB 마이그레이션은 통과했다. 접근성은 이 도구 환경에서 가능한 근사 검증(CSS zoom 대신 유효 폭 축소, 실제 NVDA 대신 접근성 트리 감사, 실제 모바일 키보드 대신 뷰포트 높이 축소)을 수행해 결함 2건(보완 21, 22)을 발견·수정했다. 다음 항목만 로컬 자동 검증으로 실제 운영 조건을 완전히 증명할 수 없어 아직 PASS가 아니다.

1. 브라우저에서의 실제 200% zoom(pinch 또는 ctrl+zoom), 실제 NVDA 등 screen reader 음성 출력, 실제 모바일 OS 가상 키보드로 근사 검증 결과를 재확인한다.
2. NARROW 375×400급 극단적으로 얕은 유효 높이(가상 키보드가 화면 절반 이상을 덮는 경우)에서 채팅 입력창이 여전히 몇 px 잘리는 residual을 해소한다 — 현재의 flex-shrink 기반 완화로는 한계가 있어, sticky 입력창처럼 채팅 스크롤 구조를 바꾸는 별도 작업이 필요할 수 있다.
3. 채팅 announcer(보완 21)와 입력창 clip 방지(보완 22)를 지키는 회귀 테스트를 추가한다.
4. 위 항목을 끝낸 뒤 WP-G를 최종 완료로 닫는다.

## 현재 판정

코드 단위 회귀, production build, 핵심 반응형·keyboard, 실제 계정 REST/STOMP 협업과 강퇴 차단, Kakao·Google provider 장애 격리, Redis·RabbitMQ process 및 CDC backlog 복구는 통과했다. 접근성은 근사 검증으로 채팅 스크린리더 낭독 폭주와 짧은 뷰포트 입력창 clip 결함을 찾아 수정했지만, 실기기·실제 AT 확인과 375×400급 극단값 residual은 아직 남아 있다. WP-G는 **핵심 협업 E2E와 주요 process·provider 장애 복원 완료, 접근성 실기기 확인·잔여 극단값 대기** 상태다. 남은 수동 gate를 수행하기 전에는 release 완료로 표기하지 않는다.
