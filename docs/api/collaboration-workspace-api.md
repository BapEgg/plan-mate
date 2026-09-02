# PlanMate 협업 워크스페이스 API·이벤트 계약

> 상태: WP-C DAY 자동차 경로 + WP-D 채팅 + WP-E 장소 변경 제안/투표/일정 revision vertical slice 반영
> 관련: `docs/collaboration-workspace-spec.md` v1.1 §8, `docs/adr/0001~0005-*.md`
> 이 문서와 `com.planmate.common.exception.ErrorCode` 구현체 전체, `com.planmate.common.realtime.RealtimeEventType`는
> `ErrorEventRegistryContractTest`로 서로 어긋나지 않는지 고정한다. 새 값은 코드·테스트·이 문서를 함께 바꾼다.

## 1. 기존 재사용 endpoint (WP-A가 확장)

| method | path | 변경 |
| --- | --- | --- |
| `GET` | `/api/trips` | 없음 |
| `POST` | `/api/trips` | 없음 |
| `DELETE` | `/api/trips/{tripId}` | 없음 |
| `GET` | `/api/trips/{tripId}` | **추가 필드**: 응답 최상위 `timezone`(IANA zone id, ADR-0005), `itineraries[].version`(ADR-0002). 멤버 목록은 이제 `ACTIVE` interval만 포함한다(ADR-0001). |
| `GET` | `/api/trips/{tripId}/itinerary-place-views` | 없음 |
| `POST` | `/api/trips/{tripId}/itinerary-generations` | 없음 |
| `GET` | `/api/trips/{tripId}/itinerary-generations/{id}` | 없음 |
| `GET` | `/api/trips/{tripId}/itinerary-generations/latest` | 없음 — 단, frontend는 이 호출 실패를 trip 조회 실패와 분리해서 처리한다(§5) |
| STOMP `SUBSCRIBE` | `/topic/trips/{tripId}/events` | 없음(계속 멤버십 검사). 연결 뒤 멤버십을 잃으면 서버가 session을 강제로 끊는다(ADR-0003) |

`TripDetailResponse.Itinerary.version`은 trip 내 단조 증가 정수다. `TripDetailResponse.timezone`은 향후 chat cutoff·vote deadline·edit lock 판정에 쓰일 IANA zone id이며, WP-A 시점에는 모든 trip이 `Asia/Seoul`이다.

## 2. 공통 command 규칙 (모든 신규 write endpoint, WP-B부터 적용)

- 현재 membership·role을 서버가 다시 검사한다. UI가 action을 숨기는 것과 무관하게 서버는 항상 재검사한다.
- `Idempotency-Key` header(`com.planmate.common.web.IdempotencyKey.HEADER_NAME`)를 받는다. 같은 key의 재전송은 최초 처리 결과를 그대로 반환하고 부작용을 다시 실행하지 않는다.
- itinerary version에 의존하는 command는 `baseItineraryId` 또는 `expectedVersion`을 받는다. base가 current와 다르면 `409 DATA_CONFLICT`.
- 성공 transaction 안에서 outbox event를 기록한다(ADR-0004의 command 경로를 재사용하거나, 향후 realtime 경로가 필요하면 같은 outbox row 저장 규칙을 따른다).
- 접근 불가 resource는 존재 여부를 노출하지 않고 `404`로 응답한다(membership 없는 trip은 `TRIP_NOT_FOUND`).

## 3. Error code registry

`ErrorCode` interface(`com.planmate.common.exception.ErrorCode`)를 구현하는 domain별 enum이 registry다. 새 domain은 새 enum을, 기존 domain에 코드를 추가할 때는 해당 enum에 상수를 추가한다 — 별도 전역 enum을 만들지 않는다.

### 3.1 공통(`CommonErrorCode`)

| code | HTTP | 의미 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 요청 형식/필드 검증 실패 |
| `FORBIDDEN` | 403 | 역할 부족(예: MEMBER가 OWNER 전용 command 호출) |
| `DATA_CONFLICT` | 409 | stale base version, 동시 apply, DB 제약 위반 |
| `RATE_LIMITED` | 429 | 과도한 요청 |

401은 인증 자체가 없거나 무효한 경우로, Spring Security 필터 체인이 `PlanMateException` 이전 단계에서 처리한다(도메인 `ErrorCode`가 아니다).

### 3.2 domain별(WP-A 시점 기존 값, 발췌)

| domain | 대표 code | HTTP |
| --- | --- | --- |
| `TripErrorCode` | `TRIP_NOT_FOUND` | 404 |
| `TripErrorCode` | `INVALID_TRIP_REQUEST` | 400 |
| `AuthErrorCode` | `INVALID_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `INVALID_TOKEN`, `EXPIRED_TOKEN` 등 | 401/403/400 |
| `UserErrorCode` | `USER_NOT_FOUND` | 404 |
| `ItineraryErrorCode` | `GENERATION_NOT_FOUND`, `GENERATION_NOT_READY`, `DESTINATION_NOT_RESOLVED` 등 | 404/409 |

전체 목록은 `ErrorEventRegistryContractTest.DOCUMENTED_ERROR_CODES`가 코드 기준으로 pin한다.

### 3.3 command domain error registry

새 command group마다 최소 다음 두 code가 필요하다 — 실제 enum과 HTTP status는 구현 시점에 추가한다.

| group | 예상 domain enum | 최소 code |
| --- | --- | --- |
| Membership | `MembershipErrorCode` | `MEMBERSHIP_NOT_FOUND`(404), `LAST_OWNER_CANNOT_LEAVE`(409), `TRIP_MEMBER_CAPACITY_EXCEEDED`(409) |
| Invitation/Friend | `InvitationErrorCode` | `INVITATION_EXPIRED`(409), `INVITATION_ALREADY_ACCEPTED`(409), `DUPLICATE_INVITATION`(409) |
| Route | `ItineraryErrorCode` | `ROUTE_QUOTA_EXCEEDED`(429), `ROUTE_PROVIDER_TIMEOUT`(504), `ROUTE_PROVIDER_UNAVAILABLE`(503), `ROUTE_PROVIDER_REQUEST_FAILED`(500) |
| Chat | `ChatErrorCode` | `DUPLICATE_CLIENT_MESSAGE_ID`(200/idempotent replay, 예외 아님), `MESSAGE_DELETE_WINDOW_EXPIRED`(409), `MESSAGE_DELETE_FORBIDDEN`(403), `INVALID_REPLY_TARGET`(400), `MESSAGE_ALREADY_DELETED`(409), `INVALID_REACTION`(400) |
| Proposal/Vote | `ProposalErrorCode`, `VoteErrorCode` | `PROPOSAL_NOT_FOUND`(404), `STALE_BASE_VERSION`(409), `PROPOSAL_NOT_READY`(409), `PROPOSAL_VOTE_BOUND`(409), `DUPLICATE_ACTIVE_PROPOSAL`(409), `INVALID_PROPOSAL`(400), `PROPOSAL_PLACE_UNRESOLVED`(422), `PROPOSAL_ROUTE_NOT_FOUND`(422), `ITINERARY_WINDOW_CLOSED`(409), `VOTE_NOT_FOUND`(404), `VOTE_ALREADY_CLOSED`(409), `NOT_ELIGIBLE_VOTER`(403), `VOTE_CANCEL_FORBIDDEN`(403) |
| Regeneration | `RegenerationErrorCode` | `REGENERATION_NOT_FOUND`(404), `REGENERATION_ALREADY_ACTIVE`(409), `REGENERATION_NOT_READY`(409), `REGENERATION_STALE_BASE`(409), `REGENERATION_INVALID_RANGE`(400), `REGENERATION_NO_REPLACEMENT`(400), `REGENERATION_FIXED_ITEM_CONFLICT`(422), `REGENERATION_WINDOW_CLOSED`(409) |

## 4. Realtime event registry

envelope(`com.planmate.common.realtime.RealtimeEventEnvelope`): `eventId`, `schemaVersion`, `type`, `tripId`, `occurredAt`, `payload`. client는 모르는 `type`/`schemaVersion`을 무시하고 화면을 실패시키지 않는다.

destination:

- trip topic `/topic/trips/{tripId}/events` — 비개인 정보만. SUBSCRIBE 시 멤버십 검사, 연결 중 멤버십 상실 시 서버가 session을 강제로 끊는다(ADR-0003).
- 개인 destination `/user/queue/trips/{tripId}/events` — `PrivateRealtimeEventPublisher.sendToUser`로 발행. 초대함, 채팅 ack, unread 등 개인 payload 전용.

`RealtimeEventType`(`com.planmate.common.realtime.RealtimeEventType`) 상수:

| 값 | destination | 상태 |
| --- | --- | --- |
| `ITINERARY_GENERATION_STATUS_CHANGED` | trip topic | WP-A, 발행 중 |
| `MEMBERSHIP_CHANGED` | trip topic | WP-B 예약 |
| `INVITATION_RECEIVED` | 개인 | WP-B 예약 |
| `CHAT_MESSAGE_SENT` | trip topic | WP-D 발행 중 — 새 message와 한 단계 reply preview 즉시 merge |
| `CHAT_MESSAGE_DELETED` | trip topic | WP-D 발행 중 — `messageId`를 받은 client는 해당 message REST snapshot 재조회 |
| `CHAT_REACTION_CHANGED` | trip topic | WP-D 발행 중 — 사용자별 `reactedByMe`를 위해 해당 message REST snapshot 재조회 |
| `CHAT_UNREAD_CHANGED` | 개인 | WP-D 예약 |
| `CHAT_TYPING_UPDATED` | trip topic | WP-D 발행 중 — 본문 없이 member별 6초 expiry 갱신 |
| `MEMBER_PRESENCE_CHANGED` | trip topic | WP-D 발행 중 — 여행방 subscription 기준 접속 상태 변경 |
| `VOTE_OPENED` | trip topic | WP-E 발행 중 — 투표 목록 REST 재조회 신호 |
| `VOTE_CLOSED` | trip topic | WP-E 발행 중 — 마감 결과 REST 재조회 신호 |
| `ITINERARY_REVISION_APPLIED` | trip topic | WP-E 발행 중 — current pointer가 바뀌었으므로 client는 trip/일정 REST 재조회 |

## 5. Frontend workspace state 계약

`frontend/src/pages/trip/workspace/workspaceState.ts`가 정의하는 root state:

```
LOADING | READY | EMPTY_ITINERARY | GENERATING_WITHOUT_CURRENT | GENERATING_WITH_CURRENT
| REFRESHING | READ_ONLY | MEMBERSHIP_LOST | SESSION_EXPIRED | PARTIAL_ERROR
```

trip 상세와 최신 generation 조회는 **독립적으로 실패**한다 — generation 조회 실패는 `PARTIAL_ERROR`(해당 patch만) 상태를 만들 뿐 trip 자체 조회 실패로 번지지 않는다. 우선순위는 spec §7과 동일: 인증 실패 > 여행방 삭제/멤버십 상실 > lifecycle read-only > 공통 연결 장애 > panel별 상태.

## 6. 신규 command group 최소 계약 (WP-B~F가 채울 API, WP-A는 구현하지 않음)

spec §8 표를 그대로 계약 skeleton으로 고정한다. 최종 URL·DTO 필드는 각 package 시작 시 이 문서를 갱신해 확정한다.

| group | 최소 command/query | base package |
| --- | --- | --- |
| Membership | title update, list/manage members, remove MEMBER, leave, transfer owner | WP-B |
| Invitation/Friend | inbox, friend request, exact-email lookup, invite send/accept/decline/cancel | WP-B |
| Route | DAY route snapshot, candidate detour, personal preview | WP-C |
| Chat | history, send, by-client-id, read receipt, delete, reply, reaction, search | WP-D |
| Presence | trip presence snapshot, connect/heartbeat/disconnect policy | WP-D |
| Proposal | create/validate/review/cancel/apply, affected range | WP-E |
| Vote | list, create, my ballot, cancel, deadline close/result | WP-E |
| Revision | current pointer, history, restore proposal | WP-E |
| Regeneration | full/partial request, status, review, apply/reject | WP-F |

각 command는 §2의 공통 규칙(membership 재검사, idempotency key, base version, outbox, 404 비노출)을 반드시 따른다.

### 6.1 WP-C 확정: DAY 자동차 경로 조회

`GET /api/trips/{tripId}/routes/days/{dayNumber}`

- 인증된 ACTIVE 멤버만 조회한다. `trips.current_itinerary_id`가 가리키는 version의 해당 DAY를 읽는다.
- DAY item의 인접한 두 장소마다 Kakao Mobility 표준 자동차 길찾기 `GET /v1/directions`를 호출한다. 다중 경유지 API는 사용하지 않는다.
- 장소 좌표를 못 얻은 구간은 전체 조회를 실패시키지 않고 `LOCATION_UNRESOLVED`, provider가 경로를 반환하지 않은 구간은 `ROUTE_NOT_FOUND`로 반환한다.
- provider timeout·장애·일일 한도는 각각 504·503·429로 응답한다. frontend는 이 경우 기존 일정과 marker를 유지하고 검증되지 않은 직선을 그리지 않는다.

응답:

```json
{
  "itineraryId": 505,
  "itineraryVersion": 1,
  "dayNumber": 1,
  "provider": "KAKAO",
  "status": "READY",
  "legs": [
    {
      "fromItemId": 1,
      "toItemId": 2,
      "sequence": 1,
      "status": "READY",
      "distanceMeters": 12300,
      "durationSeconds": 1540,
      "geometry": [{ "latitude": 34.8, "longitude": 128.6 }],
      "verifiedAt": "2026-09-01T00:00:00Z"
    }
  ]
}
```

`status`는 모든 leg가 `READY`면 `READY`, 하나라도 좌표/경로를 확인하지 못하면 `PARTIAL`이다. 응답의 `itineraryId`와 `itineraryVersion`은 client가 현재 timeline과 route의 동일 version 여부를 검증하는 guard다.

frontend overlay는 local development에서 활성화된다. production에서는 Kakao geometry를 Google 지도에 표시하는 공급자 약관·출처 검토가 끝난 환경만 `VITE_DAY_ROUTE_ENABLED=true`로 명시적으로 연다.

### 6.2 WP-D 확정: 채팅 삭제·답장·반응

기존 채팅 query/command:

| method | path | 의미 |
| --- | --- | --- |
| `GET` | `/api/trips/{tripId}/chat/messages` | 최신 30개 및 cursor history, `since` 사용 시 reconnect gap |
| `POST` | `/api/trips/{tripId}/chat/messages` | `clientMessageId`, `body`, 선택 `replyToMessageId`로 전송 |
| `GET` | `/api/trips/{tripId}/chat/messages/by-client-id/{clientMessageId}` | 전송 결과가 불명확한 메시지 확인 |
| `GET` | `/api/trips/{tripId}/chat/messages/{messageId}` | 삭제·반응 event 뒤 authoritative message snapshot 조회 |
| `DELETE` | `/api/trips/{tripId}/chat/messages/{messageId}` | 작성자만 server `sentAt` 기준 5분 안에 전체 삭제. 반복 호출은 tombstone 반환 |
| `PUT` | `/api/trips/{tripId}/chat/messages/{messageId}/reaction` | `{ "reaction": "LIKE" | "ACKNOWLEDGED" }`; 기존 내 반응을 교체 |
| `DELETE` | `/api/trips/{tripId}/chat/messages/{messageId}/reaction` | 내 반응 제거, 없는 경우에도 현재 snapshot 반환 |
| `GET` | `/api/trips/{tripId}/chat/messages/search?q=&cursor=` | 삭제되지 않은 `USER_TEXT` 본문을 방·membership interval 안에서 최신순 검색 |
| `GET` | `/api/trips/{tripId}/chat/messages/{messageId}/context` | 검색 결과 원문 앞뒤 메시지 snapshot |
| `GET` | `/api/trips/{tripId}/presence` | ACTIVE 참여자의 현재 workspace subscription 접속 snapshot |

`ChatMessageResponse`는 기존 필드에 `replyTo`, `deleted`, `deletedAt`, `deletableUntil`, `reactions[]`, `mentions[]`를 추가한다. 삭제 시 원문 `body`는 빈 문자열로 지우고 응답에는 `삭제된 메시지입니다.` tombstone을 반환한다. 답장은 현재 membership interval 안에서 보이는 삭제 전 메시지만 새 대상으로 선택할 수 있으며, 이미 만들어진 답장의 preview는 원문 삭제 뒤에도 tombstone으로 남는다. 반응은 ACTIVE 참여자의 것만 count/name 목록에 포함한다. 멘션은 현재 ACTIVE 참여자를 member ID로 선택한 경우만 저장하며 offset은 Unicode code point 기준이다.

`CHAT_MESSAGE_SENT` payload에는 한 단계 reply preview가 포함된다. `CHAT_MESSAGE_DELETED`, `CHAT_REACTION_CHANGED` payload는 `messageId`만 변경 신호로 보내며 client가 위 단건 GET을 다시 호출한다. 이는 반응의 `reactedByMe`가 사용자마다 다른 값을 갖기 때문이다.

typing은 client가 `/app/trips/{tripId}/chat/typing`에 `STARTED | HEARTBEAT | STOPPED`, `clientSessionId`, `clientEventId`만 보내며 message body·길이는 보내지 않는다. server는 마지막 갱신 6초 뒤 session을 만료하고 `CHAT_TYPING_UPDATED`를 발행한다. presence는 인증·멤버십 검사를 통과한 trip topic subscription을 ONLINE으로 집계하며 예상치 못한 disconnect에는 10초 grace를 둔다.

검색 query는 NFC 정규화 후 2~100 code point, 영문 대소문자 무시, literal substring 규칙이다. 최초 20건과 opaque cursor를 사용하고 `searchSnapshotSequence` 이후 새 메시지를 현재 결과에 끼워 넣지 않는다. `INVALID_SEARCH_QUERY`는 길이·cursor·query 문맥이 유효하지 않을 때 `400`으로 반환한다.

### 6.3 WP-E 확정: 장소 변경 제안·투표·일정 revision

| method | path | 의미 |
| --- | --- | --- |
| `GET` | `/api/trips/{tripId}/itinerary-proposals` | 변경안 목록 |
| `POST` | `/api/trips/{tripId}/itinerary-proposals` | current itinerary의 장소 한 개를 교체하는 검증된 변경안 생성 |
| `POST` | `/api/trips/{tripId}/itinerary-proposals/{proposalId}/apply` | OWNER가 투표에 올리지 않은 READY 변경안을 즉시 새 revision으로 반영 |
| `GET` | `/api/trips/{tripId}/itinerary-votes` | 투표와 내 선택, 참여 집계 조회. 마감된 OPEN 투표는 조회 transaction에서 먼저 닫는다. |
| `POST` | `/api/trips/{tripId}/itinerary-votes/proposals/{proposalId}` | ACTIVE 멤버가 변경안을 24시간 투표로 전환 |
| `PUT` | `/api/trips/{tripId}/itinerary-votes/{voteId}/ballot` | `{ "choice": "CHANGE" | "KEEP_CURRENT" }`; 마감 전 덮어쓰기 가능 |
| `DELETE` | `/api/trips/{tripId}/itinerary-votes/{voteId}` | 제안자 또는 현재 OWNER가 OPEN 투표 취소 |
| `GET` | `/api/trips/{tripId}/itinerary-revisions` | immutable 일정 version 이력과 current 여부 조회 |

변경안 생성은 `baseItineraryId`, `baseItineraryVersion`, DAY/item, 대체 Google place ID, 시작 시각, 체류 시간을 받는다. 서버는 place 좌표와 앞뒤 자동차 경로를 검증하며, current base가 달라졌거나 장소가 이미 지난 시각이면 생성/적용을 거부한다. 투표에 묶인 변경안은 이후 direct apply할 수 없다.

투표 voter snapshot은 open 시점 ACTIVE 멤버다. 최소 참여 수는 1명이면 1, 2명 이상이면 `max(2, ceil(N/2))`이며 `CHANGE > KEEP_CURRENT`일 때만 통과한다. 전원이 투표하면 즉시 마감하고, 아니면 24시간 deadline에 마감한다. 탈퇴·강퇴된 멤버의 voter/ballot은 무효화한 뒤 다시 계산한다.

적용은 기존 itinerary row를 수정하지 않는다. 모든 DAY/item을 다음 단조 증가 version으로 복사하고 target item만 교체한 뒤, `trips.current_itinerary_id`를 base 일치 조건으로 원자적으로 갱신한다. 동시 변경으로 base가 달라지면 `409 STALE_BASE_VERSION`이며 기존 current를 유지한다.

### 6.4 WP-F 확정: 전체·부분 일정 다시 만들기

| method | path | 의미 |
| --- | --- | --- |
| `POST` | `/api/trips/{tripId}/itinerary-regenerations` | OWNER가 current version을 기준으로 전체 또는 같은 DAY의 연속 구간 재생성을 시작 |
| `GET` | `/api/trips/{tripId}/itinerary-regenerations/latest` | 가장 최근 작업 상태와 적용 전 비교 초안 조회 |
| `GET` | `/api/trips/{tripId}/itinerary-regenerations/{regenerationId}` | 특정 작업의 authoritative 상태 조회 |
| `POST` | `/api/trips/{tripId}/itinerary-regenerations/{regenerationId}/apply` | OWNER가 READY_FOR_REVIEW 초안을 새 immutable version으로 적용 |
| `POST` | `/api/trips/{tripId}/itinerary-regenerations/{regenerationId}/reject` | OWNER가 초안을 거절하고 current 일정 유지 |

생성 요청은 `baseItineraryId`, `expectedItineraryVersion`, `scope`, 선택 `additionalRequest`를 받는다. `FULL`에는 범위 필드를 보내지 않으며 `PARTIAL`은 `dayNumber`, 같은 DAY의 `startItemId`·`endItemId`, 범위 안 `fixedItemIds`를 보낸다. 서버가 item의 실제 순서를 기준으로 시작·종료를 정규화하며, 범위 안 최소 한 장소는 교체 대상으로 남아야 한다.

일정 후보 수집과 manual/fixture 응답은 기존 generation pipeline을 그대로 사용한다. regeneration generation의 `ai-request`에는 scope, current item, `KEEP | REPLACE`, 추가 요청이 포함된다. 응답은 기존 AI validation을 통과해야 하며, 부분 생성은 범위 밖 item을 base에서 다시 합쳐 변경하지 않는다. 고정 item은 같은 placeId·체류 시간·상대 순서를 유지하고 시작 시각만 기존 값의 ±30분까지 허용한다.

상태는 `GENERATING → READY_FOR_REVIEW → APPLIED | REJECTED`이며 후보 수집 실패는 `FAILED`, base version이 달라진 적용은 `STALE` 또는 `409 REGENERATION_STALE_BASE`다. `GENERATING`과 `READY_FOR_REVIEW` 동안 `trips.current_itinerary_id`는 바뀌지 않는다. 적용 transaction만 새 itinerary version과 DAY/item을 저장하고 current pointer를 조건부 갱신한다.

동일한 `apply` 또는 `reject` command 재시도는 이미 끝난 동일 결과를 반환하며 새 version이나 event를 중복 생성하지 않는다.

`ITINERARY_REGENERATION_CHANGED`는 작업 상태가 바뀌었다는 trip topic 신호다. payload는 `regenerationId`, `generationId`, `status`, 선택 `appliedItineraryId`만 포함하며 client는 REST snapshot으로 복구한다. 실제 적용은 기존 `ITINERARY_REVISION_APPLIED`도 함께 발행한다.

WP-F 오류 코드는 `REGENERATION_NOT_FOUND`, `REGENERATION_ALREADY_ACTIVE`, `REGENERATION_NOT_READY`, `REGENERATION_STALE_BASE`, `REGENERATION_INVALID_RANGE`, `REGENERATION_NO_REPLACEMENT`, `REGENERATION_FIXED_ITEM_CONFLICT`, `REGENERATION_WINDOW_CLOSED`다.

## 7. Frontend API 모듈 경계

`frontend/src/api/`:

- `trips.ts` — 기존, 변경 없음
- `tripWorkspace.ts` — 워크스페이스가 쓰는 기존 호출의 얇은 재노출
- `routes.ts` — DAY 자동차 경로 query와 DTO를 구현했다. 후보 detour·개인 preview는 아직 타입/계약 단계다.
- `membership.ts`, `chat.ts` — 각 work package 구현 상태를 따른다.
- `votes.ts`, `revisions.ts` — WP-E proposal/vote/ballot/apply 및 revision history fetch를 구현했다.
- `regenerations.ts` — WP-F 전체·부분 다시 만들기, 상태·비교, 적용·거절 fetch를 구현했다.
