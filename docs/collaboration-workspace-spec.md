# PlanMate 협업형 여행 상세 워크스페이스 실행 명세

> 상태: 요구사항 검증 완료 / API 계약 작성 전  
> 문서 버전: 1.0  
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

- 중앙 지도는 좌표를 CSS/SVG에 배치한 prototype이며 지도 SDK가 아니다.
- route 선은 실제 provider geometry가 아니다.
- 우측 채팅·투표는 empty UI뿐이며 저장·전송 API가 없다.
- 실시간 권한은 `SUBSCRIBE` 시점만 검사하며 이미 연결된 session의 멤버십 상실을 무효화하지 못한다.
- 최신 일정은 `createdAt desc`로 선택하며 명시적 current pointer·version guard가 없다.

### 3.3 구현되지 않음

- 제품용 초대·친구·나가기·내보내기·방장 이전
- 실제 지도 SDK·polyline·route cache
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
- DB 변경과 외부 event 발행은 기존 outbox 경계를 재사용한다.
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

### 10.1 교차 기능 병목

| 기반 계약 | 의존 기능 | 잘못 병렬화할 때 문제 |
| --- | --- | --- |
| membership interval | 내보내기, 채팅 visibility, unread, vote voter, presence | 재가입 권한·과거 대화가 서로 다르게 계산됨 |
| itinerary current version | proposal, vote 적용, 복원, 전체·부분 재생성 | 오래된 proposal이 최신 일정을 덮어씀 |
| route provider | 좌측 이동 정보, map polyline, 후보 +N분, 부분 일정 validation | 화면마다 다른 추정 시간 사용 |
| timezone model | 여행 상태, edit lock, chat cutoff, TRANSIT | server와 client가 서로 다른 날짜로 판단 |
| realtime/event registry | generation, membership, chat, vote, invite, presence | event type 충돌·누락·중복 처리 |
| shared selection state | DAY, timeline, marker, mobile pane | 좌측과 지도가 다른 item을 선택 |

이 기반 계약은 소비 기능보다 먼저 한 명의 contract owner가 확정한다.

### 10.2 현재 코드 병목

- `TripDetailPage.tsx` 약 871줄, `TripDetailPage.css` 약 2324줄, `frontend/src/api/trips.ts` 약 317줄이다.
- 여러 에이전트가 이 세 파일에서 map·chat·vote·membership을 동시에 구현하면 merge conflict와 상태 결합이 발생한다.
- 기능 구현 전에 동작을 바꾸지 않는 분해 작업이 필요하다.

목표 frontend 경계:

```text
frontend/src/pages/trip/workspace/
  TripWorkspacePage.tsx
  workspaceState.ts
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

backend는 기존 package 규칙을 유지하되 membership, route, chat, proposal/vote와 revision의 application/service/repository 경계를 분리한다. 하나의 거대한 TripDetail response에 모든 실시간·pagination 데이터를 넣지 않는다.

### 10.3 외부 공급자 차단점

- 지도 표시·Places 검색은 Google 기준으로 진행할 수 있다.
- 국내 자동차 route는 현재 Google Routes 검증에서 성공 응답 안에 route가 없었던 사례가 있다.
- Phase 3 전에 Kakao Mobility 또는 NAVER route를 실제 거제 좌표로 검증하고 라이선스·요금·quota·polyline 지원을 기록해야 한다.
- 공급자 확정 전 route 숫자·polyline·후보 추가 이동시간을 구현하지 않는다.
- frontend용 browser map key와 backend용 server key를 분리하고 referrer/IP/API restriction을 적용한다. key 값을 문서·log·commit에 넣지 않는다.

### 10.4 실시간 보안 차단점

- 현재 interceptor는 SUBSCRIBE 시점만 멤버십을 검사한다.
- MEMBER 제거 transaction 뒤 해당 사용자의 기존 trip subscription/session을 서버가 무효화하고 client가 `MEMBERSHIP_LOST`로 전환해야 0-2가 완료된다.
- invite inbox는 trip topic이 아니라 인증된 사용자 private destination이 필요하다.
- presence는 persistent member count와 분리하고 Redis TTL 또는 동등한 ephemeral store를 사용한다.

### 10.5 범위 과대화 방지

- 친구·초대 전체를 map보다 먼저 완성하면 상세 핵심 읽기·지도 검증이 지연된다. 개발 중에는 격리된 local membership을 사용하고 production membership은 독립 workstream으로 진행한다.
- 채팅 reply·reaction·typing·검색·browser 알림은 확정 요구사항이지만 저장·전송·history·gap recovery가 통과한 뒤 순차 추가한다.
- 개인 길찾기·대체 route·CUSTOM_PIN·TRANSIT는 shared map·route와 proposal 기반이 먼저 통과한 뒤 추가한다.
- 전체·부분 재생성 UI는 immutable version·proposal 적용이 없으면 구현하지 않는다.

## 11. 에이전트 작업 분할

contract owner가 shared schema/API/event를 먼저 확정한 뒤 아래 workstream을 병렬화할 수 있다.

| workstream | 책임 | 선행 조건 |
| --- | --- | --- |
| Foundation | migration 순서, API/error/event registry, workspace state | 없음 |
| Shell/Schedule | frontend 분해, header, DAY/timeline, selection | Foundation read contract |
| Map/Route | map adapter, marker, route API/cache | route provider 결정 |
| Membership | remove/leave/invite/friend/transfer, live revocation | membership interval |
| Chat | message/history/reconnect 후 확장 기능 | membership interval, event registry |
| Proposal/Vote | proposal, ballot, current version 적용 | itinerary version, route validation |
| Generation | 전체·부분 재생성과 proposal review | itinerary version, proposal |
| QA | E2E, responsive, accessibility, failure injection | 각 phase deliverable |

충돌 방지 규칙:

- DB migration 번호는 Foundation owner 한 명만 배정한다.
- 공통 `application.yaml`, security config, realtime envelope, `App.tsx`와 workspace root state는 동시에 수정하지 않는다.
- 공용 contract 변경은 소비 agent 작업 전에 먼저 merge한다.
- agent는 할당 범위 밖의 대규모 rename·formatting을 하지 않는다.
- dirty worktree의 기존 변경은 사용자 소유로 간주하고 덮어쓰지 않는다.

필수 handoff:

```text
목표 / 완료 상태
조사한 기존 계약
변경 파일
API·DB·event 변경
feature flag·환경 변수(값 제외)
실행한 테스트와 결과
직접 검증 URL·계정·시나리오
미검증 항목과 위험
다음 작업의 정확한 선행 조건
```

## 12. 구현 순서와 단계 종료 조건

기존 0~8단계 방향을 유지한다.

### Phase 0 — 기반 계약

완료됨:

- 0-1 기준 여행 `1530/1415/505`, 4 DAY·23 item 검증
- 0-2A `test` OWNER, `local1/local2` MEMBER 개발 기준선

남은 순서:

1. API gap matrix와 error/event registry 문서
2. frontend workspace/API 파일 무동작 분해
3. membership interval ADR와 migration
4. OWNER 전용 MEMBER 내보내기 API
5. REST·새 STOMP 구독 차단
6. 기존 STOMP session 무효화와 열린 화면의 메인 이동
7. workspace 상태 모델과 event gap recovery

종료 조건:

- 세 계정 role과 접근이 일치한다.
- 비회원·제거된 MEMBER는 REST·신규/기존 실시간 접근이 모두 종료된다.
- session 만료·empty itinerary·partial error에서 shell이 깨지지 않는다.

### Phase 1 — 읽기 전용 일정

- API 데이터만으로 DAY·timeline·item selection을 구현한다.
- place display 부분 실패를 격리한다.
- desktop/mobile selection 복원을 검증한다.

### Phase 2 — 실제 지도

- map SDK adapter, DAY marker, bounds와 양방향 item selection
- 장소 compact panel과 attribution
- 추정 route는 아직 표시하지 않는다.

### Phase 3 — 실제 route

- 국내 provider 검증·결정
- server route API/cache, polyline, 좌측 이동 구간
- provider failure에도 일정·marker 조회 유지

### Phase 4 — 실시간 채팅

순서:

1. 저장·history·plain text 전송·idempotency
2. STOMP event와 reconnect gap recovery
3. unread recipient/read receipt와 membership visibility
4. delete·reply·reaction
5. typing·presence·mention·search·notification

각 하위 단계는 두 계정 새로고침·재접속과 비회원 거절 테스트를 통과한다.

### Phase 5 — proposal·투표·적용

- current itinerary version과 proposal validation
- 지도/좌측 장소 제안
- vote snapshot·deadline·ballot·cancel
- 통과 결과의 단 한 번 새 version 적용

### Phase 6 — OWNER 전체 일정 다시 만들기

- 기존 generation pipeline을 재사용한다.
- 생성 중 current 일정은 계속 사용한다.
- 성공 결과는 proposal/review 뒤 새 version, 실패는 current 유지다.

### Phase 7 — 부분 일정 다시 만들기와 고급 편집

- 연속 범위·fixed anchor·±30분·경계 route validation
- CUSTOM_PIN, version restore와 필요 시 TRANSIT
- 범위 밖 item은 동일하게 보존한다.

### Phase 8 — 통합 안정화

- optimistic conflict, duplicate command, disconnect와 session expiry
- WIDE/MEDIUM/NARROW와 editor resize
- keyboard, screen reader, reduced motion와 200% zoom
- 성능, provider quota, event lag와 failure metric

## 13. 검증 기준

모든 마이크로 단위:

1. 작업 전 관련 code·migration·test와 dirty diff를 조사한다.
2. API/DB/event 영향과 변경 파일을 먼저 기록한다.
3. 가장 작은 backend test를 실행하고 범위가 넓으면 전체 suite를 실행한다.
4. frontend는 `npm.cmd run lint`, `npm.cmd run build`를 통과한다.
5. UI 변경은 browser에서 desktop과 mobile, loading/empty/error/success/disabled/focus를 확인한다.
6. 실제 확인하지 못한 상태를 성공으로 보고하지 않는다.
7. 실행 결과와 남은 위험을 이 문서의 진행 기록 또는 별도 handoff에 남긴다.

필수 통합 시나리오:

1. OWNER와 두 MEMBER가 같은 거제 여행방과 23개 item을 본다.
2. DAY 3 장소를 timeline과 map에서 번갈아 선택한다.
3. 두 사용자가 message를 주고받고 한쪽 새로고침 뒤 같은 history를 본다.
4. MEMBER가 장소를 제안하고 유권자가 투표한다.
5. 결과가 새 itinerary version으로 한 번만 적용되고 timeline·map이 같은 version을 본다.
6. OWNER가 부분 재생성을 요청하고 범위 밖 일정이 유지된다.
7. MEMBER의 OWNER command가 거절된다.
8. MEMBER를 내보내면 열린 REST·STOMP·private cache 접근이 끝난다.
9. WebSocket을 끊었다 복구해 message·vote·itinerary가 REST와 일치한다.

## 14. 출시 전 관측 지표

- REST·STOMP 인증/멤버십 거절률
- WebSocket 연결·재연결·gap recovery 성공률
- chat send ack 지연·실패·중복 방지
- vote deadline·apply conflict·STALE 비율
- 전체·부분 generation 성공·실패·소요 시간
- route provider 성공·empty·timeout·quota 오류와 cache hit
- frontend panel error와 session expiry redirect

message body, draft, exact CUSTOM_PIN, email, token과 API key는 log·metric에 기록하지 않는다.

## 15. 현재 미결정·차단 항목

제품 행동 요구사항은 상단·좌측·중앙·우측·반응형까지 확정됐다. 남은 것은 구현 기술 계약이다.

1. 국내 자동차 route provider: Kakao Mobility 또는 NAVER 실제 검증 뒤 결정
2. membership interval을 기존 table 확장으로 할지 별도 history table로 할지 ADR
3. itinerary current pointer/version schema ADR
4. 사용자 private WebSocket destination과 기존 session 강제 무효화 방식
5. production browser notification의 HTTPS·permission·service worker 범위

이 다섯 항목을 임의 구현으로 숨기지 않는다. 관련 phase 시작 전에 API/ADR와 테스트 기준을 먼저 작성한다.

## 16. 바로 다음 작업

코드 기능을 추가하기 전에 다음 문서를 작성한다.

`docs/api/collaboration-workspace-api.md`

첫 계약 범위는 `0-2B MEMBER 내보내기`다.

- OWNER 전용 remove command
- OWNER/self/비소속 대상 거절
- membership 종료 결과와 event
- 대상의 REST·새 STOMP 구독 차단
- 다음 단위에서 기존 subscription 무효화에 필요한 session/event 정보

API 계약 승인 후 migration·service·controller·security test 순으로 구현한다.
