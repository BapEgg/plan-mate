# PlanMate 협업형 여행 상세 워크스페이스 실행 명세

> 상태: 요구사항 검증 완료 / 대형 work package 실행 계획 포함 / API 계약 작성 전
> 문서 버전: 1.1
> 대상 화면: `/trips/{tripId}`  
> 기준 데이터: `tripId=1530`, `generationId=1415`, `itineraryId=505`  
> 최종 검토일: 2026-08-31  
> 과거 인터랙티브 결정 원문: [결정 기록 archive](archive/collaboration-workspace-spec-decision-log-2026-08-31.md)

## 1. 문서 역할과 사용 규칙

이 문서는 여행 상세 화면을 구현하는 모든 AI 에이전트의 단일 제품 요구사항 기준이다. 화면 아이디어를 모은 결정 로그가 아니라, 구현 순서·권한·데이터 불변식·완료 조건을 압축한 실행 명세다.

문서 우선순위:

1. 이 실행 명세
2. 이후 작성할 API·이벤트 계약서와 ADR
3. 자동 테스트로 고정된 계약
4. archive의 과거 결정 기록

archive는 결정 배경을 확인할 때만 사용한다. archive의 반복 문구나 예전 미확정 항목이 이 문서와 다르면 이 문서를 따른다. 에이전트는 archive 전체를 기본 컨텍스트로 읽지 않는다.

상태 표기:

- `필수`: 현재 목표에 반드시 포함
- `후속`: 요구사항은 확정됐지만 앞선 기반 기능 뒤에 구현
- `제외`: 현재 제품 범위에서 만들지 않음
- `차단`: 외부 선택이나 선행 데이터 계약 없이는 구현 금지

사용자는 코드베이스를 직접 검토하지 않는다. 따라서 각 작업 에이전트가 코드 조사, 영향 분석, 구현, 자동 테스트, 실제 화면·API 검증과 결과 보고를 끝까지 책임진다.

## 2. 제품 목표와 범위

핵심 작업은 참여자가 같은 여행 일정과 지도를 보며 대화하고, 제안과 투표로 합의된 변경을 최신 일정에 안전하게 반영하는 것이다.

화면 구조:

- 상단: 여행방 제목·목적지·날짜·상태·참여자·방장 작업
- 좌측: 선택한 날짜의 시간순 일정과 이동 구간
- 중앙: 실제 조작 가능한 지도, 장소 marker, route와 장소 정보
- 우측: 채팅과 투표

필수 범위:

- 최신 일정 조회, DAY 탐색과 일정·지도 선택 동기화
- 실제 지도 marker와 실제 provider route
- 내부 여행방 멤버십, 초대, 나가기, MEMBER 내보내기와 방장 이전
- 저장되는 실시간 채팅과 재연결 복구
- 장소 제안, 투표, 일정 새 버전 반영
- OWNER 전용 전체·부분 일정 다시 만들기
- 동시 변경, 권한, 세션 만료, 반응형과 접근성

제외 범위:

- 항공권·숙소 예약, 결제, 비용 정산
- 실시간 가격·운항·영업 여부 보장
- 오프라인 지도
- 채팅 사진·파일·음성·위치 첨부
- 채팅 신고, 개인 차단, 사용자별 메시지 숨김
- 대화 기록 파일 내보내기
- 검증되지 않은 직선거리나 추정 시간을 실제 route처럼 표시
- 사용자 확인 없이 일정 변경을 조용히 덮어쓰기

## 3. 현재 코드 기준선

### 3.1 구현됨

- 로컬·OAuth 인증, access/refresh token과 세션 만료 처리
- 여행 생성·목록·상세·OWNER 삭제
- 여행 조건과 Google Places 후보 수집
- 일정 생성 요청, 수동 AI handoff/fixture, validation과 저장
- local `itinerary-fixture` profile은 실제 GPT 호출 지점만 fixture로 대체하며 validation·저장·COMPLETED 처리는 기존 흐름을 통과한다. production 기본값은 비활성이다.
- portfolio/local 시연용 일정 fixture와 route 통합 시나리오는 국내 여행지만 사용한다. 해외 route·timezone 동작은 production 지원 범위를 별도로 확정하기 전까지 시연 완료 조건에 포함하지 않는다.
- 최신 generation 조회와 최신 itinerary read model
- 일정 item별 place display·좌표 조회
- `/ws/events`, trip topic, JWT 연결 인증과 구독 시 멤버십 검사
- `RealtimeEventEnvelope`와 일정 생성 상태 event
- 3열 상세 화면 prototype과 mobile pane 전환
- local test user와 개발용 trip membership initializer

검증된 기준:

| 항목 | 값 |
| --- | --- |
| 여행 | `1530 / 거제 가족여행` |
| 일정 생성 | `1415 / COMPLETED` |
| 최신 일정 | `505` |
| DAY별 item | `5 / 5 / 7 / 6`, 총 23건 |
| OWNER | `test`, user `2588` |
| MEMBER | `local1` user `2623`, `local2` user `2624` |

### 3.2 일부만 구현됨

- 중앙 지도는 Google Maps JavaScript SDK와 실제 place 좌표 marker를 사용하고 DAY·timeline 선택을 동기화한다. 다만 Places library loading, 좌표 지연 도착 뒤 bounds 갱신, 장소 상세 panel과 provider 오류 복구는 안정화 전이다.
- route 선·이동 시간·거리는 아직 표시하지 않으며 화면용 provider geometry API도 없다. 기존 Google Routes adapter는 AI 일정 validation용 duration/distance 경계다.
- 우측 채팅·투표는 고정 preview UI뿐이며 저장·전송 API가 없다. 기본 production 화면에서 실제 데이터로 오인되지 않도록 demo fixture 격리 또는 명확한 미연결 상태가 필요하다.
- WIDE 3열과 NARROW 단일 pane은 구현됐지만 MEDIUM의 `일정 | 지도 | 여행방` 전환 동작과 tab 접근성은 보완 전이다.
- 실시간 권한은 `SUBSCRIBE` 시점만 검사하며 이미 연결된 session의 멤버십 상실을 무효화하지 못한다.
- 최신 일정은 `createdAt desc`로 선택하며 명시적 current pointer·version guard가 없다.

### 3.3 구현되지 않음

- 제품용 초대·친구·나가기·내보내기·방장 이전
- 실제 route polyline·route snapshot/cache·후보 detour
- itinerary proposal, immutable version history와 current pointer
- 투표·ballot·마감 scheduler·자동 적용
- 채팅·읽음·reply·reaction·typing·presence·검색·알림
- CUSTOM_PIN과 TRANSIT 저장 모델
- 장소별 timezone·UTC instant 기반 lifecycle

## 4. 도메인 불변식

공통 용어:

| 용어 | 의미 |
| --- | --- |
| 여행방 | 하나의 trip과 그 ACTIVE 멤버가 공유하는 협업 경계 |
| membership interval | 한 사용자가 초대 수락부터 나가기·내보내기까지 ACTIVE였던 한 참여 구간 |
| current itinerary | 현재 모든 멤버에게 기본 노출되는 하나의 일정 version |
| proposal | current를 바로 바꾸지 않는 검토·validation 가능한 변경안 |
| vote | proposal을 공동 결정하기 위한 유권자 snapshot과 ballot 집합 |
| snapshot | event 유실·재접속 뒤 REST로 복구 가능한 서버 기준 read model |
| `CUSTOM_PIN` | Places에 없는 위치를 사용자가 좌표로 지정한 private 장소 |
| `TRANSIT` | 서로 다른 출발·도착 장소와 시간대를 가진 장거리 이동 item |

### 4.1 멤버십과 권한

- 여행방 역할은 활성 `OWNER` 한 명과 `MEMBER`로 구성한다.
- 여행 조건의 `companionCount`와 협업 계정 수는 별개다.
- OWNER와 MEMBER만 여행 상세 REST·STOMP에 접근한다.
- OWNER만 제목 수정, 초대 관리, MEMBER 내보내기, 방장 이전, 전체·부분 일정 다시 만들기와 직접 적용을 수행한다.
- MEMBER는 채팅, 장소 제안과 투표를 수행하며 current itinerary를 직접 바꾸지 못한다.
- UI에서 action을 숨기는 것과 별개로 모든 command에서 서버가 현재 역할을 다시 검사한다.
- 나가기·내보내기·재가입을 별도 membership interval로 표현한다. 재가입 사용자가 과거 interval 권한을 자동 회복하면 안 된다.
- 내보낸 사용자의 과거 작성자 snapshot과 감사 기록은 유지하되 현재 방 데이터 접근은 즉시 제거한다.
- 이미 연결된 WebSocket도 멤버십 상실 뒤 새 event를 받지 못해야 한다.
- vote 모델이 추가된 뒤에는 membership 종료와 OPEN vote 유권자·ballot 무효화를 같은 command transaction에서 처리한다. vote가 아직 없는 초기 removal 구현은 이 확장 지점을 계약과 테스트에 남긴다.

현재 `trip_members(trip_id,user_id)` 영구 unique 구조는 재가입 interval을 표현하지 못한다. 멤버십 기능 확장 전에 status·joined/left boundary를 가진 새 모델 또는 별도 interval table을 ADR로 확정해야 한다.

### 4.2 일정 버전

- 적용된 일정은 immutable version으로 보존한다.
- trip에는 명시적인 current itinerary pointer 또는 동등한 원자적 선택 모델이 있어야 한다.
- 모든 proposal·투표·복원·재생성 command는 `baseItineraryId` 또는 version을 받는다.
- base가 current가 아니면 `409`로 거절하고 client는 최신 일정을 다시 조회한다.
- `createdAt desc`만으로 current를 결정하거나 기존 row를 덮어쓰지 않는다.
- 같은 일정 item의 화면·지도 식별자는 `itemId`다. `placeId`는 반복 방문할 수 있으므로 item identity로 사용하지 않는다.
- 투표 통과와 일정 pointer 변경은 중복 실행되지 않도록 idempotency와 optimistic/conditional update를 사용한다.

현재 schema는 generation마다 itinerary 한 건만 허용하지만 trip current version을 표현하지 않는다. proposal·vote·재생성 구현 전 version ADR과 migration이 먼저다.

### 4.3 시간

- 서버·DB의 deadline과 권한 판단 기준은 UTC instant다.
- 화면 일정은 장소의 IANA timezone 기준 현지 시각으로 보여준다.
- device timezone과 device clock은 편집 가능 여부·여행 상태·투표 마감 판단에 사용하지 않는다.
- chat은 여행지 lifecycle zone에서 종료일 당일까지 작성 가능하고 다음 날부터 읽기 전용이다.
- 여행 중에는 시작하지 않은 현재·미래 item만 변경 가능하고 종료 후에는 읽기 전용이다.
- 장거리 `TRANSIT`는 출발·도착 장소, timezone과 UTC instant를 한 item에 가진다.

현재 itinerary item은 `LocalTime`만 저장한다. 읽기 전용 국내 일정은 기존 값으로 표시할 수 있지만 timezone 기반 잠금·국제 TRANSIT·일정 변경을 구현하기 전에 시간 모델 migration이 필요하다.

### 4.4 데이터와 실시간 event

- REST snapshot이 authoritative state다.
- WebSocket은 변경 사실과 식별자를 전달하며, 일정·멤버십·투표 변경 뒤 client는 필요한 REST snapshot을 다시 조회한다.
- 채팅처럼 append-only인 데이터만 event payload를 즉시 merge하고 재연결 시 REST로 gap을 복구한다.
- 공통 envelope는 `eventId`, `schemaVersion`, `type`, `tripId`, `occurredAt`, `payload`를 유지한다.
- client는 알 수 없는 schema/type을 무시하고 전체 화면을 실패시키지 않는다.
- DB 변경과 외부 event 발행은 기존 outbox 저장 경계를 재사용하되, 현재 없는 dispatcher·retry·delivery checkpoint를 C3/outbox ADR에 따라 추가한다.
- event 순서만 신뢰하지 않고 entity version·sequence로 중복과 역전을 처리한다.

## 5. 화면 기능 명세

### 5.1 상단

여행 정보:

- 전체 화면 workspace의 고정 행이며 page body 전체를 길게 scroll하지 않는다.
- desktop은 왼쪽 여행 정보, 오른쪽 참여자·관리의 2열이다.
- 제목 아래 목적지·날짜·`D-N | 여행 중 | 지난 여행`을 plain text hierarchy로 표시한다.
- 반복 divider, pill, 큰 hero card와 장식용 gradient를 사용하지 않는다.
- 제목은 OWNER만 같은 자리에서 수정한다. Unicode grapheme 기준 최대 30자, 줄바꿈·연속 공백은 한 공백으로 정규화한다.

참여자:

- 최대 5명 avatar를 표시하고 6명부터 `+N` button으로 전체 목록을 연다.
- 정렬은 `OWNER → 나 → 나머지 참여 순서`, 동률은 user ID 오름차순이다.
- 전체 목록은 desktop popover, mobile bottom sheet다.
- 현재 여행방을 구독 중인 사용자는 정적인 초록 점과 `접속 중` text로 표시한다. offline은 초록 점을 표시하지 않는다.
- 초록 점은 presence이며 현재 device의 연결 오류 문구를 대체하지 않는다.

멤버 관리:

- OWNER는 MEMBER 행의 `⋯`에서 `여행방에서 내보내기`를 실행한다. OWNER 자신과 다른 OWNER는 대상이 아니다.
- MEMBER는 본인 `여행방 나가기`를 사용할 수 있다. OWNER는 먼저 방장을 넘기거나 여행방을 삭제해야 한다.
- 참여자 popover는 확인용이고 초대 취소·내보내기·방장 이전은 별도 관리 drawer에서 수행한다.
- 내보내기와 OPEN vote의 해당 유권자 제거·ballot 무효화는 같은 transaction 경계에서 처리한다.

초대·친구·방장 이전은 확정된 후속 범위다.

- 내부 초대만 지원한다. 외부 메신저 deep link·비회원 가입 후 복귀는 제외한다.
- 친구 선택 또는 정확한 email로 기존 PlanMate 계정을 확인한 뒤 내부 초대함으로 보낸다.
- 친구 관계와 여행방 멤버십은 독립적으로 수락한다.
- 협업 계정은 OWNER 포함 최대 20명이며 ACTIVE + 유효 PENDING 초대가 자리를 사용한다.
- 여행 초대는 7일, 방장 이전 요청은 48시간 유효하다.
- 초대함은 메인페이지 봉투 button과 `여행 초대 | 친구 요청 | 방장 요청` tab으로 제공한다.
- 방장 이전은 대상 MEMBER 수락 뒤 원자적으로 OWNER와 MEMBER 역할을 교체한다.

### 5.2 좌측 일정

- 상단 고정 DAY tab은 `1일차`와 실제 날짜·요일을 함께 표시한다.
- 선택한 DAY의 일정만 세로 timeline으로 보여주고 지도도 같은 DAY만 표시한다.
- 장소 row는 sequence marker, 시작 시각, 장소명, 체류 시간을 가진다.
- 장소 사이에는 실제 route 성공 시에만 이동수단·시간·거리를 표시한다.
- 좌측에는 반복 card·장소 thumbnail·빈 사진 placeholder를 사용하지 않는다.
- 선택 상태와 지도 marker는 같은 `itemId`를 사용한다.
- place display 조회가 실패해도 순서·시각은 유지하고 장소 정보만 부분 오류로 표시한다.

OWNER 편집:

- panel header의 `일정 수정`에서 `선택 구간 다시 만들기 | 전체 일정 다시 만들기`를 연다.
- 부분 범위는 같은 DAY의 연속 item만 선택한다.
- 범위 안 item은 `바꾸기 | 그대로 두기`로 구분한다.
- 고정 item은 place, 상대 순서, 체류 시간을 유지하고 시작 시각은 기본 ±30분 안에서만 route·운영시간과 함께 조정한다.
- 충돌 시 고정값을 임의 변경하지 않고 원인을 반환한다.
- 생성 결과는 `PROPOSED`이며 OWNER가 비교 후 적용하기 전까지 current 일정은 유지한다.

제안·복원:

- OWNER와 MEMBER 모두 Places 장소나 CUSTOM_PIN을 proposal로 만들 수 있다.
- OWNER는 validation 뒤 직접 적용하거나 투표에 맡길 수 있다.
- MEMBER는 직접 적용하지 않고 투표를 연다.
- 같은 영향 범위의 OPEN vote는 한 건만 허용하고 다른 proposal은 `BLOCKED/NEEDS_REVIEW`로 보존한다.
- 적용 전 proposal은 timeline 위 `검토 중인 변경` 영역에만 두고 확정 timeline에 섞지 않는다.
- 과거 version 복원도 과거 pointer를 되돌리는 것이 아니라 current 기준 새 proposal·version을 만든다.
- 재접속 conflict에서 Git diff 같은 3-way merge UI는 만들지 않고 최신 일정에서 다시 수정한다.

### 5.3 중앙 지도

- Google Maps/Places를 기본 표시·검색 경계로 사용하되 SDK는 `TripMapAdapter` 뒤에 둔다.
- 사용자가 mouse drag·wheel, touch pan·pinch, keyboard와 map control로 직접 이동·확대할 수 있어야 한다.
- 최초 진입과 DAY 변경 때만 선택 DAY bounds에 자동 맞춘다. 이후 일반 event로 사용자의 viewport를 강제로 되돌리지 않는다.
- `일정 전체 보기`로 수동 재맞춤한다.
- current marker는 timeline sequence 번호를 사용하고 선택 marker만 크기·outline·z-index로 강조한다.
- route geometry가 없으면 marker 사이 직선을 실제 route처럼 그리지 않는다.
- 일정·marker·하단 compact 장소 panel은 동일한 selected item을 공유한다.

장소 panel:

- 기본 정보는 장소명, 일정 시각·체류 시간, 주소와 현재 context action이다.
- photo·평점·영업시간은 provider가 실제로 반환하고 attribution 조건을 충족할 때만 펼친 상세에서 표시한다.
- 없는 field의 빈 placeholder를 만들지 않는다.

검색과 proposal:

- 상단 autocomplete와 명시적 `이 지역에서 다시 찾기`를 제공하며 map 이동만으로 자동 검색하지 않는다.
- 처음 10곳, 사용자 `더 보기` 뒤 최대 20곳을 표시한다.
- filter는 `전체 | 관광지 | 음식점 | 카페 | 숙소 | 교통` 중 하나다.
- 일정 gap에서 시작한 검색은 앞·뒤 route 주변을 우선한다.
- 후보 정렬의 `+N분`은 실제 provider로 계산한 추가 이동시간이 있을 때만 표시한다.
- 후보 선택만으로 일정을 변경하지 않는다. `경로 보기 → 일정에 넣기 → 시간·체류 입력 → validation → 비교`를 거친다.
- current route와 후보 route를 함께 비교하되 current는 낮추고 후보와 앞·뒤 context marker를 명확히 표시한다.

개인 위치:

- `내 위치`를 사용자가 선택한 뒤 일회성 위치 권한만 요청한다.
- 위치는 해당 device의 개인 preview이며 다른 멤버에게 전송하지 않는다.
- `여기서 길찾기`와 최대 3개 대체 route도 개인 state이며 shared 일정에는 반영하지 않는다.

CUSTOM_PIN:

- 일반 map click이 아니라 명시적 `위치 직접 지정` mode에서만 만든다.
- 임시 pin은 한 건이며 새 위치 선택으로 교체한다.
- `latitude/longitude`, trusted `canonicalAddress`, 필수 `displayName`, 선택 `accessNote`를 분리한다.
- 정확한 좌표·주소·메모는 ACTIVE 멤버와 route provider에 필요한 범위에서만 제공하며 AI prompt·log·알림에 원문을 넣지 않는다.

### 5.4 우측 채팅·투표

- `채팅 | 투표` tab이며 첫 진입은 채팅, 같은 trip session에서는 마지막 tab을 복원한다.
- 새 event가 와도 tab을 강제로 바꾸지 않는다.
- 투표 tab 숫자는 내가 아직 선택하지 않은 OPEN vote 수다.
- 채팅 tab에는 가장 가까운 투표 마감과 필요한 action을 한 줄로만 안내한다.

채팅 핵심:

- 1차 사용자 message는 plain text만 지원하며 service notice는 구조화된 system message다.
- 최신 30개부터 opaque cursor로 이전 30개씩 조회한다.
- `clientMessageId`로 중복 저장을 막고 reconnect 뒤 server 저장 여부를 확인한다.
- 자동 재전송하지 않는다. 미저장 확인 뒤 사용자가 message별로 다시 보낸다.
- desktop Enter 전송·Shift+Enter 줄바꿈, mobile은 visible 보내기 button을 기본으로 한다.
- 같은 발신자의 5분 이내 연속 message를 시각적으로 묶는다.
- 내 message는 옅은 cornflower, 상대는 neutral, system notice는 bubble이 아닌 text row다.

읽음과 visibility:

- message 생성 당시 ACTIVE 수신자 snapshot에서 작성자를 제외한 미확인 수를 `2 → 1 → 없음`으로 표시한다.
- 채팅 tab·document가 활성이고 message가 viewport에 실제 노출됐을 때 읽음으로 처리한다.
- 새 멤버는 현재 membership interval 참여 이후 message만 본다.
- 나갔다 재가입하면 새 interval 이후 message만 보며 과거 interval은 다시 열리지 않는다.
- 나가기·내보내기 사용자는 unread 대상에서 제거하되 읽은 것으로 기록하지 않는다.

확정된 후속 채팅 기능:

- 본문 수정 없음, 작성자만 server 시각 5분 이내 전체 삭제와 tombstone
- 한 단계 reply와 원문 한 줄 preview
- message당 `좋아요 | 확인했어요` 중 하나의 reaction, count와 현재 참여자 이름 popover
- 최대 두 명 이름을 표시하는 typing indicator
- 현재 ACTIVE 참여자 개인 mention, group mention 없음
- 삭제되지 않은 USER_TEXT keyword 검색과 원문 이동
- 앱 안 unread 기본, 사용자 opt-in browser background 알림
- 여행방별 browser 알림 단순 on/off
- 여행 종료 다음 날부터 읽기 전용, 여행방이 존재하는 동안 history 보존

연결 상태:

- 정상 상태는 상단 presence 초록 점 외에 `연결됨` text를 반복하지 않는다.
- 현재 device 실시간 연결이 2초 넘게 끊기면 composer 가까이 복구 안내를 표시한다.
- REST history는 가능하면 유지하고 server 확정 전송만 막는다.
- reconnect 성공 뒤 subscription, REST gap, UNKNOWN message를 모두 교정해야 복구 완료다.

투표:

- 선택지는 `제안 장소로 변경 | 현재 일정 유지`, 1인 1표이며 자동 찬성·기권은 없다.
- 유권자는 OPEN 시점 ACTIVE 멤버 snapshot이다. 이후 합류자는 현재 vote에 참여하지 않는다.
- 최소 참여는 N=1이면 1명, N≥2이면 `max(2, ceil(N/2))`다.
- 최소 참여를 충족하고 변경 표가 유지 표보다 많을 때만 통과한다. 동률·참여 부족은 현재 일정 유지다.
- 유권자 나가기·내보내기 시 snapshot과 해당 ballot을 제거하고 기준을 재계산한다.
- 기본 마감은 server 시각 24시간이며 유효 유권자 전원이 투표하면 즉시 닫는다.
- OWNER가 결과를 예상해 임의 조기 마감하지 않는다.
- 제안자와 현재 OWNER는 OPEN vote를 취소할 수 있으나 취소는 통과·부결이 아니다.
- OWNER가 `함께 정하기`를 선택한 proposal은 투표 결과를 우회해 직접 적용할 수 없다.
- 통과 뒤 current version·route·시간을 다시 검증하고 성공할 때만 새 version을 자동 적용한다.

## 6. 반응형과 접근성

| mode | layout |
| --- | --- |
| WIDE | 일정 · 지도 · 채팅/투표 3열 |
| MEDIUM | 일정 · 지도 2열 + modal 오른쪽 여행방 drawer |
| NARROW | 하단 `일정 | 지도 | 여행방` 중 한 pane |

- mobile 첫 진입은 일정이며 같은 session의 마지막 pane을 복원한다. 유효 deep link가 최우선이다.
- mobile 지도에도 shared DAY tab을 둔다.
- 일정 row는 같은 pane에서 펼치고 `지도에서 보기`로만 map pane으로 전환한다. map의 `일정에서 보기`는 대응 row로 직접 이동한다.
- mobile OWNER 편집은 full-screen editor다.
- editor는 실제 변경이 있을 때만 나가기 확인을 하고 같은 browser session에서만 draft를 복구한다.
- base version이 바뀐 draft는 자동 merge하지 않고 최신 일정에서 다시 시작한다.
- resize·회전으로 mode가 바뀌어도 editor instance·draft·job identity를 유지한다.
- 가상 키보드가 실제 viewport를 줄일 때만 하단 tab bar를 숨긴다.
- semantic HTML, keyboard, visible focus, dialog focus trap, live region, 44px touch target과 `prefers-reduced-motion`을 지킨다.
- 색상만으로 상태·선택·오류를 전달하지 않는다.

## 7. 공통 상태와 오류 우선순위

우선순위:

1. 인증 실패
2. 여행방 삭제 또는 멤버십 상실
3. 여행 lifecycle 읽기 전용
4. 공통 연결 장애
5. panel별 loading·empty·error

- P0는 token 재발급을 한 번 시도하고 실패하면 로그인으로 이동한다.
- P1은 private 내용과 cache를 제거하고 메인으로 이동할 수 있게 한다.
- 여러 panel 최신 상태가 2초 이상 보장되지 않을 때만 상단 아래 얇은 공통 상태 줄을 표시한다.
- 마지막 검증 내용을 가리거나 흐리지 않는다.
- 읽기와 local draft는 유지하고 server 확정 action만 관련 연결이 복구될 때까지 차단한다.
- panel 하나만 실패하면 해당 panel 가까이 원인과 복구 action을 표시한다.
- API code, WebSocket, JSON, generation 같은 구현 용어를 사용자 문구에 노출하지 않는다.

필수 workspace state:

- `LOADING`
- `READY`
- `EMPTY_ITINERARY`
- `GENERATING_WITHOUT_CURRENT`
- `GENERATING_WITH_CURRENT`
- `REFRESHING`
- `READ_ONLY`
- `MEMBERSHIP_LOST`
- `SESSION_EXPIRED`
- `PARTIAL_ERROR`

## 8. 선행 API·데이터 계약

API 세부 DTO와 error code는 별도 `docs/api/collaboration-workspace-api.md`에서 먼저 고정한다. 아래는 필요한 endpoint group이며 최종 URL은 기존 convention 검토 뒤 확정한다.

기존 재사용:

- trip detail, itinerary place views, latest generation, generation create/detail
- place autocomplete·destination search
- trip event topic과 공통 envelope

신규 계약 group:

| group | 최소 command/query |
| --- | --- |
| Membership | title update, list/manage members, remove MEMBER, leave, transfer owner |
| Invitation/Friend | inbox, friend request, exact-email lookup, invite send/accept/decline/cancel |
| Route | DAY route snapshot, candidate detour, personal preview |
| Chat | history, send, by-client-id, read receipt, delete, reply, reaction, search |
| Presence | trip presence snapshot, connect/heartbeat/disconnect policy |
| Proposal | create/validate/review/cancel/apply, affected range |
| Vote | list, create, my ballot, cancel, deadline close/result |
| Revision | current pointer, history, restore proposal |
| Regeneration | full/partial request, status, review, apply/reject |

공통 command 규칙:

- 현재 membership·role server 재검사
- stable idempotency key
- base version 또는 expected state
- 성공 transaction 안에서 outbox event 기록
- `400` validation, `401` auth, `403` role, `404` inaccessible resource, `409` stale/state conflict, `429` rate limit 구분
- 접근 불가 resource의 존재 여부를 불필요하게 노출하지 않음

## 9. 데이터 migration 순서

한 번에 거대한 migration을 만들지 않는다. 아래 순서대로 각각 forward/rollback 또는 복구 전략과 테스트를 가진다.

1. 멤버십 lifecycle
   - ACTIVE/LEFT/REMOVED interval, 단일 OWNER 제약, 재가입 boundary
   - invitation, friend relation, ownership transfer는 별도 table
2. itinerary version
   - current pointer, monotonic version, base reference와 source
   - proposal·affected range·validation result
3. vote
   - eligible voter snapshot, ballot unique, deadline와 terminal state
4. route snapshot/cache
   - itinerary version + DAY + provider + mode key, geometry·duration·verifiedAt
5. chat
   - room sequence, clientMessageId unique, message/tombstone/reply
   - membership interval visibility와 recipient/read model
6. timezone·CUSTOM_PIN·TRANSIT
   - IANA zone, UTC instant, private location field와 access audit

migration 전 현재 fixture `1530/1415/505`와 기존 여행 조회가 유지되는 회귀 테스트를 먼저 작성한다.

## 10. 의존성·중복·병목 리뷰

### 10.1 먼저 고정해야 하는 공통 기반

기능 번호 순서가 아니라 아래 계약의 의존성을 기준으로 구현한다. `HARD` 기반이 고정되지 않은 소비 기능은 production code를 만들지 않고 contract test·interface까지만 준비한다.

| 기반 ID | 공통 계약 | HARD 의존 기능 | 먼저 잘못 구현할 때 생기는 문제 |
| --- | --- | --- | --- |
| C0 | API/error/idempotency/event registry | 모든 command·실시간 기능 | endpoint·error·event 이름이 기능마다 달라지고 reconnect 복구가 불가능해짐 |
| C1 | membership interval·단일 OWNER·현재 권한 판정 | 내보내기, 나가기, 초대, 채팅 visibility, unread, vote voter, presence | 재가입 사용자가 과거 대화·투표 권한을 되찾거나 제거된 사용자가 event를 계속 수신함 |
| C2 | immutable itinerary version·current pointer·conditional apply | proposal, vote 적용, 복원, 전체·부분 재생성, route snapshot | 오래된 변경안이 최신 일정을 덮어쓰고 timeline·map이 다른 version을 표시함 |
| C3 | private realtime destination·session registry·gap recovery | 초대함, chat ack, unread, membership revocation, vote notice, presence | trip topic에 개인 정보가 노출되거나 기존 session을 끊지 못함 |
| C4 | UTC instant·여행 lifecycle zone | chat cutoff, edit lock, vote deadline, TRANSIT | client·server·사용자 위치마다 다른 날짜에 잠김 |
| C5 | route provider·mode mapping·cache key·geometry 형식 | 좌측 이동 구간, map polyline, 후보 detour, 부분 재생성 validation | 같은 구간의 시간·경로가 화면마다 달라지고 provider 장애가 전체 일정을 가림 |
| C6 | frontend workspace state·itemId selection | DAY, timeline, marker, mobile pane, editor | 선택·loading·error가 panel마다 따로 움직이고 event 반영 중 화면이 깨짐 |

필수 결정 순서:

1. C0, C1, C2, C3, C4의 ADR·API skeleton과 회귀 테스트를 같은 Foundation package에서 고정한다.
2. C5는 Kakao Mobility 자동차 길찾기 API의 계약·quota·cache·geometry ADR을 실제 route 숫자·polyline 소비 기능보다 먼저 merge한다.
3. C6는 읽기 전용 화면 동작을 유지하는 frontend 분해에서 고정한다.
4. 이후 membership·map/route·chat은 서로 다른 package 경계에서 병렬화할 수 있다.
5. proposal/vote는 C1·C2·C5가 통과한 뒤, regeneration은 proposal apply가 통과한 뒤 시작한다.

### 10.2 구현 의존 그래프

```text
WP-A Foundation + Workspace 안정화
  ├─ WP-B Membership + Invitation/Friend + live revocation
  ├─ WP-C Map + Route + place proposal entry
  └─ WP-D Chat core schema/history/send contract

WP-B membership visibility/revocation
  └─ WP-D unread/presence/rejoin visibility 완료

WP-B + WP-C + C2
  └─ WP-E Proposal + Vote + Revision apply

WP-C + WP-E
  └─ WP-F Full/Partial Regeneration + editor

WP-B + WP-C + WP-D + WP-E + WP-F
  └─ WP-G 통합 안정화·접근성·관측성
```

`WP-B`와 `WP-C`는 완전히 병렬이다. `WP-D`의 schema·history·send 계약은 WP-A 뒤 시작할 수 있지만 membership visibility·unread·presence 완료는 WP-B의 remove/rejoin 계약을 소비한다. 단일 agent가 수행할 때도 하나를 끝낸 뒤 다음을 시작하되 공통 계약을 임의 변경하지 않는다. 공통 계약 변경이 필요하면 WP-A 문서와 contract test를 먼저 수정하고 모든 소비 package를 다시 검증한다.

### 10.3 현재 코드와 UI 병목

- 현재 `TripDetailPage.tsx`는 약 801줄이며 `TripDetailPage.css` 약 2707줄과 `TripWorkspacePortfolio.css` 약 1712줄을 동시에 import한다.
- `TripMapView.tsx`가 실제 Google marker를 렌더링하지만 Places library loading과 비동기 좌표 도착 뒤 bounds 갱신을 보완해야 한다.
- 우측 고정 chat/vote preview는 production에서 실제 데이터로 오인될 수 있다. demo fixture는 명시적 local/portfolio flag로 격리하고 기본값은 honest empty/disabled state다.
- MEDIUM switcher는 세 pane 중 선택한 pane을 일관되게 표시해야 하며 `tab/tabpanel` keyboard·ARIA 계약을 맞춰야 한다.
- `frontend/src/api/trips.ts`, `RealtimeWebSocketConfig`, `RealtimeStompChannelInterceptor`, security config, migration 번호와 workspace root state는 공통 충돌 지점이다.
- 현재 outbox는 event row 저장과 retention 정리만 있고 relay/dispatcher가 없다. 실시간 발행은 같은 process의 `AFTER_COMMIT` listener와 in-memory simple broker를 사용하므로 process crash·다중 instance에서 전달 보장이 달라진다.
- frontend에는 자동 unit/component test runner가 없다. 새 dev dependency가 필요하면 라이선스를 먼저 확인하고 WP-A에서 한 번만 도입한다.
- 최신 generation 조회 실패가 전체 trip 조회 실패로 번지지 않도록 read concern을 분리한다.
- 한 agent가 feature 구현과 대규모 formatting·rename을 같이 수행하지 않는다.

목표 frontend 경계:

```text
frontend/src/pages/trip/workspace/
  TripWorkspacePage.tsx
  workspaceState.ts
  workspaceEvents.ts
  components/WorkspaceHeader.tsx
  schedule/
  map/
  room/chat/
  room/vote/
  membership/
  editor/

frontend/src/api/
  tripWorkspace.ts
  membership.ts
  routes.ts
  chat.ts
  votes.ts
  revisions.ts
```

backend는 기존 package 규칙을 유지하되 `membership`, `route`, `chat`, `proposal`, `vote`, `revision`의 application/service/repository 경계를 분리한다. 실시간 pagination 데이터까지 하나의 거대한 TripDetail response에 넣지 않는다.

### 10.4 공용 파일 소유권과 merge 규칙

| 공용 지점 | 단일 소유 package | 소비 package 규칙 |
| --- | --- | --- |
| Flyway migration 번호·schema baseline | WP-A | 다른 package는 예약된 번호만 사용하고 기존 migration 수정 금지 |
| error code·command header·event type registry | WP-A | 새 값은 registry와 contract test를 먼저 변경 |
| WebSocket config·interceptor·session registry | WP-A, 이후 WP-B handoff | WP-D/E는 공개된 publisher/subscriber port만 사용 |
| itinerary current pointer | WP-A | WP-C/E/F는 repository를 직접 우회하지 않음 |
| workspace root state·App route | WP-A | 하위 panel은 domain hook과 callback으로만 연결 |
| provider key·application config | WP-C | key 값은 commit/log/document에 넣지 않음 |

각 work package 시작 전 dirty diff와 이전 package handoff를 읽는다. 검증 전 package를 섞어 커밋하지 않는다. 사용자 승인 전 commit·push하지 않으며, 승인된 checkpoint 뒤 다음 package를 시작한다.

### 10.5 외부 공급자와 보안 차단점

- 지도 표시·Places 검색은 Google 경계를 유지한다. browser map key와 backend server key를 분리하고 referrer/IP/API restriction을 적용한다.
- 공유 일정의 국내 자동차 route provider는 **Kakao Mobility 자동차 길찾기** `GET /v1/directions`로 고정한다. 카카오의 별도 **다중 경유지 길찾기** `POST /v1/waypoints/directions`는 현재 범위에서 제외한다.
- Kakao가 앱 단위로 기본 적용하는 자동차 길찾기 일일 무료 쿼터 **10,000건**을 provider 최종 차단선으로 사용한다. 추가 쿼터·유료 제휴는 신청하거나 활성화하지 않는다. 이 값은 PlanMate가 콘솔에서 임의로 지정하는 custom limit이 아니므로, 서버에도 프로젝트 전체 호출을 선점하는 원자적 일일 usage guard(`KAKAO_DIRECTIONS_DAILY_LIMIT=10000`)를 둔다. DB/공유 저장소의 `provider + operation + KST date` counter를 외부 호출 전에 증가시켜 재시작·동시 instance에서도 10,000건을 넘기지 않으며 실패 호출과 retry도 각각 사용량으로 센다. 한도 도달 시 외부 호출·Google/NAVER 자동 fallback을 하지 않고 `ROUTE_QUOTA_EXCEEDED`를 반환하며 기존 일정·marker·마지막 검증 route를 유지한다.
- route는 itinerary version + DAY + mode + 좌표 hash로 cache하고, 일정이 바뀐 DAY만 다시 조회한다. timeout 재시도도 일일 usage에 포함해 무제한 재시도로 한도를 넘기지 않는다.
- 지도 표시·Places 검색은 현재 Google 경계를 유지하므로 Kakao route geometry를 Google 지도에 표시하는 방식의 약관·출처 표기를 WP-C ADR에서 검증한다. 검증 전에는 provider 이름을 숨기거나 Google route로 오인시키지 않으며, 검증 실패 시 production route overlay를 차단한다.
- portfolio/local route 시연과 자동화 fixture는 국내 여행지만 사용한다. 해외 일정은 route 없음 상태를 정직하게 표시하며 임의 직선·추정 시간을 만들지 않는다.
- MEMBER 제거 transaction 뒤 기존 trip subscription/session을 서버가 무효화하고 client가 private cache를 지운 뒤 `MEMBERSHIP_LOST`로 전환해야 C1/C3가 완료된다.
- invite inbox·chat ack·개인 unread는 인증된 사용자 private destination을 사용한다. trip topic에 email·token·개인 위치를 싣지 않는다.
- local/단일 instance는 simple broker를 유지할 수 있지만 production 다중 instance를 목표로 하면 broker relay 또는 outbox dispatcher와 instance 간 event fan-out 방식을 ADR로 고정한다. durable state는 항상 REST/DB snapshot으로 복구 가능해야 한다.
- presence는 DB member count와 분리한 TTL 기반 ephemeral state다.
- exact CUSTOM_PIN, message body, AI draft, email, token과 API key는 log·metric·outbox payload에 원문으로 남기지 않는다.

### 10.6 범위 팽창 방지

- WP 하나는 작게 찢은 endpoint 하나가 아니라 사용자에게 검증 가능한 수직 기능 묶음이다. 반대로 WP 두 개의 공통 기반을 동시에 임의 변경하는 거대 diff는 금지한다.
- 채팅 확장 기능은 같은 WP-D 안에서 구현하되 `저장/history/send → reconnect → unread → delete/reply/reaction → typing/search/notification` 내부 순서를 지킨다.
- 개인 길찾기·대체 route·CUSTOM_PIN·TRANSIT는 shared route와 proposal apply가 통과한 뒤 WP-C/F의 후반 checkpoint에서 연다.
- 전체·부분 재생성은 current version·proposal review/apply 없이 직접 current itinerary를 교체하지 않는다.
- browser notification은 core collaboration을 차단하지 않는 후반 기능이다. HTTPS·permission·service worker가 준비되지 않으면 명시적으로 후속 상태를 유지한다.

## 11. 대형 work package

| package | 큰 작업 범위 | HARD 선행 | package 종료 조건 |
| --- | --- | --- | --- |
| WP-A Foundation + Workspace | API gap/error/event registry, C1~C4 ADR·migration skeleton, current pointer backfill, private realtime/session port, outbox/broker delivery ADR, frontend workspace 분해, 현재 map/UI 안정화, test harness | 없음 | 기존 `1530/1415/505` 회귀, lint/build/test, 읽기·지도·세션 오류 화면 유지, 소비 package가 사용할 contract 고정 |
| WP-B Membership + Invitation | interval, OWNER/MEMBER 권한, remove/leave/transfer, friend·email lookup, inbox, invite accept/decline/cancel, 기존 session revocation | WP-A C0/C1/C3 | 세 계정 권한 E2E, 재가입 boundary, 제거 즉시 REST·새/기존 STOMP 차단, 최대 인원·만료·중복 초대 검증 |
| WP-C Map + Route | map adapter 안정화, place panel, Kakao Mobility 자동차 길찾기 adapter, DAY route snapshot/cache/polyline, 이동 구간, search/detour | WP-A C2/C6; package 초반 C5 ADR | 실제 provider 결과만 표시, 일정·marker·route 동기화, 실패 시 일정/marker 유지, 일 10,000건 hard cap·no paid fallback·cache test |
| WP-D Chat + Presence | message schema, history/send/idempotency, private ack, reconnect gap, unread/visibility, delete/reply/reaction, typing/presence/mention/search, notification boundary | WP-A C0/C1/C3/C4; WP-B membership contract | 두 계정 새로고침·재접속·중복 전송·재가입 visibility·종료 후 read-only 검증 |
| WP-E Proposal + Vote + Revision | current version, proposal validation, 직접/투표 결정, voter snapshot/ballot/deadline, conditional one-time apply, history/restore proposal | WP-A C2; WP-B; WP-C route validation | stale base 409, 동시 apply 1회, MEMBER 직접 적용 거절, 투표 기준·멤버 제거 재계산 검증 |
| WP-F Generation + Editor | OWNER 전체/부분 재생성, fixed anchor·연속 범위, review/apply/reject, CUSTOM_PIN, 필요한 TRANSIT | WP-C; WP-E | 생성 중 current 유지, 실패 current 보존, 범위 밖 item 불변, 적용 시 새 version 1회 |
| WP-G Integration + Release | 전체 상태 우선순위, reconnect/failure injection, responsive/accessibility, session expiry, 성능·관측성, docs 정합성 | WP-B~F | 13장의 통합 gate와 사용자 시나리오 전부 통과, production default에서 demo/fixture 비활성 |

work package 내부 구현 순서는 `contract test → migration/domain → service → controller/realtime → frontend API/state → UI → integration test`다. API만 만들고 화면이 없거나 화면만 있고 저장되지 않는 상태는 package 완료가 아니다.

필수 handoff:

```text
package / 목표 / 완료 여부
읽은 기준 문서와 기존 계약
변경 파일과 소유 경계
API·DB·event·환경 변수 변경(값 제외)
자동 테스트 명령과 결과
직접 검증 URL·계정·시나리오
미검증 항목·known risk·fallback
다음 package가 의존할 정확한 contract와 version
```

## 12. 실행 웨이브와 통합 순서

기존 0~8단계의 제품 방향은 유지하되 agent 명령은 아래 웨이브 단위로 크게 내린다.

### Wave 1 — WP-A 단독

- 현재 dirty UI diff를 보존하고 실제 동작 기준선을 먼저 고정한다.
- `docs/api/collaboration-workspace-api.md`와 필요한 ADR을 작성한다.
- workspace를 기능 경계로 분해하고 기존 read-only 일정·실제 marker 동작을 유지한다.
- C0~C4 contract test와 migration/backfill 전략을 만든다.
- outbox dispatcher/broker topology와 단일·다중 instance 전달 보장 범위를 ADR로 고정한다.
- 이 웨이브가 검증·checkpoint 되기 전 다른 domain migration을 시작하지 않는다.

### Wave 2 — WP-B·WP-C·WP-D

- WP-B와 WP-C는 WP-A 뒤 병렬이며, WP-D core도 시작할 수 있다. 다만 WP-D의 unread·presence·재가입 visibility 종료 gate는 WP-B 완료 뒤 닫는다.
- 병렬 agent를 사용할 때 migration 번호, WebSocket config, workspace root state를 공유 편집하지 않는다.
- 단일 agent는 `WP-B → WP-C → WP-D` 또는 `WP-C → WP-B → WP-D` 순서로 완료 checkpoint를 남긴다. WP-D는 membership visibility 때문에 WP-B 계약을 먼저 소비해야 한다.
- 각 package 완료 뒤 전체 backend test와 frontend build를 다시 돌려 공통 계약 drift를 잡는다.

### Wave 3 — WP-E

- proposal·vote·revision을 하나의 수직 package로 구현한다.
- 투표 통과가 current pointer를 바꾸는 유일한 자동 경로이며 direct OWNER apply도 같은 validation·conditional apply service를 사용한다.
- 지도 후보·timeline proposal이 같은 affected range와 base version을 사용한다.

### Wave 4 — WP-F

- 기존 generation pipeline을 재사용하되 결과를 current에 직접 저장하지 않고 proposal/review 경계로 연결한다.
- 전체·부분 재생성, CUSTOM_PIN, restore와 TRANSIT는 공통 version/apply 규칙을 우회하지 않는다.

### Wave 5 — WP-G

- 기능 추가를 멈추고 전체 장애·권한·responsive·accessibility·quota·성능 검증만 수행한다.
- demo fixture, manual handoff와 local initializer가 production 기본 동작에 영향을 주지 않는지 확인한다.
- 문서의 baseline·endpoint·event·migration 번호를 실제 코드와 최종 동기화한다.

## 13. 검증 gate

### 13.1 모든 package 공통

1. 작업 전 관련 code·migration·test·dirty diff와 직전 handoff를 조사한다.
2. 구현 전 API·DB·event·파일 소유 영향과 변경 목록을 보고한다.
3. backend는 변경 domain test와 `backend\\gradlew.bat test`를 통과한다.
4. frontend는 test harness가 있으면 unit/component test, 항상 `npm.cmd run lint`, `npm.cmd run build`를 통과한다.
5. UI는 browser에서 WIDE/MEDIUM/NARROW와 loading/empty/error/success/disabled/focus를 확인한다.
6. migration은 빈 DB와 기존 V21 데이터 upgrade, fixture `1530/1415/505` 보존을 확인한다.
7. 실제 확인하지 못한 상태를 성공으로 보고하지 않는다.
8. 검증 agent가 diff와 실행 결과를 승인하기 전 다음 package와 섞어 commit·push하지 않는다.

### 13.2 package별 필수 gate

- WP-A: 기존 여행 상세·일정 생성·manual/fixture 흐름 회귀, session expiry와 latest generation 부분 실패 격리
- WP-B: OWNER/MEMBER/비회원 matrix, 초대 만료·중복·20명 제한, remove 중 열린 REST/STOMP 차단
- WP-C: 실제 거제 DAY route, route empty/timeout/quota, 장소 좌표 일부 실패, map key 미설정
- WP-D: 두 계정 동시 전송, 같은 `clientMessageId`, reconnect gap, unread `2→1→없음`, 나감·재가입 visibility
- WP-E: concurrent ballot/apply, stale base, deadline, member removal voter 재계산, apply idempotency
- WP-F: 생성 중 current 조회, full/partial 실패, fixed anchor conflict, 범위 밖 item hash 동일
- WP-G: 200% zoom, keyboard/screen reader, reduced motion, mobile keyboard, token refresh 실패, provider/Redis/Rabbit 장애

### 13.3 필수 통합 시나리오

1. OWNER와 두 MEMBER가 같은 거제 여행방과 23개 item을 본다.
2. DAY 3 장소를 timeline과 map에서 번갈아 선택하고 실제 route를 같은 version으로 본다.
3. 두 사용자가 message를 주고받고 한쪽 새로고침 뒤 같은 history와 unread를 본다.
4. MEMBER가 장소를 제안하고 유권자가 투표한다.
5. 결과가 새 itinerary version으로 한 번만 적용되고 timeline·map·route가 같은 version을 본다.
6. OWNER가 부분 재생성을 요청하고 범위 밖 일정이 동일하게 유지된다.
7. MEMBER의 OWNER command가 거절된다.
8. MEMBER를 내보내면 열린 REST·STOMP·private cache 접근이 끝난다.
9. WebSocket을 끊었다 복구해 message·vote·itinerary가 REST와 일치한다.
10. route provider 실패 중에도 current 일정·marker·chat은 사용할 수 있다.
11. session refresh가 실패하면 private state를 지우고 로그인으로 이동한다.
12. MEDIUM/NARROW 전환과 회전 뒤 DAY·selected item·draft·room tab을 복원한다.
13. production profile에서 fixture·demo message·manual 개발 도구가 노출되지 않는다.

## 14. 출시 전 관측 지표

- REST·STOMP 인증/멤버십 거절률
- WebSocket 연결·재연결·gap recovery 성공률
- chat send ack 지연·실패·중복 방지
- vote deadline·apply conflict·STALE 비율
- 전체·부분 generation 성공·실패·소요 시간
- route provider 성공·empty·timeout·quota 오류와 cache hit
- frontend panel error와 session expiry redirect

message body, draft, exact CUSTOM_PIN, email, token과 API key는 log·metric에 기록하지 않는다.

## 15. 결정 gate와 차단 항목

| gate | 결정 시점 | 결정 전 허용 | 결정 전 금지 |
| --- | --- | --- | --- |
| membership interval ADR | WP-A | read query·interface·fixture | remove/leave/invite/chat visibility production migration |
| itinerary version/current ADR | WP-A | 기존 read-only itinerary | proposal/vote/regeneration apply |
| private WebSocket/session revocation | WP-A | 현재 generation topic | invite/chat ack/unread와 제거된 session 유지 |
| outbox/broker delivery topology | WP-A | 단일 instance generation 알림 | 다중 instance 전달 보장 주장·복구 불가능 event payload |
| timezone/lifecycle ADR | WP-A | 국내 read-only LocalTime 표시 | chat cutoff·edit lock·TRANSIT 판단 |
| Kakao route ADR·표시 약관 검증 | WP-C 시작 | Google marker·place display, 국내 fixture | route 숫자·polyline·detour의 production 노출 |
| browser notification 범위 | WP-D 후반 | 앱 안 unread | 권한 prompt·service worker·background push |

ADR에는 선택안, 기각안, 기존 데이터 backfill, rollback/복구, 동시성 방식과 테스트를 포함한다. 차단 항목을 임시 boolean·mock 숫자·직선 route로 숨기지 않는다.

## 16. 바로 다음 실행: WP-A

다음 agent에게 MEMBER 내보내기 endpoint 하나가 아니라 `WP-A Foundation + Workspace` 전체를 맡긴다.

필수 산출물:

1. `docs/api/collaboration-workspace-api.md`
2. membership interval, itinerary current version, private realtime/session revocation, outbox/broker delivery, timezone lifecycle ADR
3. 기존 V21과 fixture `1530/1415/505`를 보존하는 migration/backfill·회귀 테스트
4. error/idempotency/event registry와 contract test
5. frontend workspace/API 분해와 shared selection/state 경계
6. 실제 map loader/bounds, honest chat/vote state, MEDIUM pane와 접근성 안정화
7. backend 전체 test, frontend lint/build/test와 WIDE/MEDIUM/NARROW browser 결과

WP-A에서는 제품용 membership/chat/vote/route command를 끝까지 구현하지 않는다. 대신 다음 package가 공통 파일을 다시 설계하지 않고 바로 수직 기능을 구현할 만큼 계약·migration 경계·test fixture·frontend slot을 완성한다.
