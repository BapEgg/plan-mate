# PlanMate 협업 워크스페이스 API·이벤트 계약

> 상태: WP-C DAY 자동차 경로 + WP-D 채팅 delete/reply/reaction/typing/presence/mention/search vertical slice 반영
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

### 3.3 예약(WP-B~F가 추가할 domain, 이름만 고정)

새 command group마다 최소 다음 두 code가 필요하다 — 실제 enum과 HTTP status는 구현 시점에 추가한다.

| group | 예상 domain enum | 최소 code |
| --- | --- | --- |
| Membership | `MembershipErrorCode` | `MEMBERSHIP_NOT_FOUND`(404), `LAST_OWNER_CANNOT_LEAVE`(409), `TRIP_MEMBER_CAPACITY_EXCEEDED`(409) |
| Invitation/Friend | `InvitationErrorCode` | `INVITATION_EXPIRED`(409), `INVITATION_ALREADY_ACCEPTED`(409), `DUPLICATE_INVITATION`(409) |
| Route | `ItineraryErrorCode` | `ROUTE_QUOTA_EXCEEDED`(429), `ROUTE_PROVIDER_TIMEOUT`(504), `ROUTE_PROVIDER_UNAVAILABLE`(503), `ROUTE_PROVIDER_REQUEST_FAILED`(500) |
| Chat | `ChatErrorCode` | `DUPLICATE_CLIENT_MESSAGE_ID`(200/idempotent replay, 예외 아님), `MESSAGE_DELETE_WINDOW_EXPIRED`(409), `MESSAGE_DELETE_FORBIDDEN`(403), `INVALID_REPLY_TARGET`(400), `MESSAGE_ALREADY_DELETED`(409), `INVALID_REACTION`(400) |
| Proposal/Vote | `ProposalErrorCode`, `VoteErrorCode` | `STALE_BASE_VERSION`(409, `DATA_CONFLICT` 재사용 가능), `VOTE_ALREADY_CLOSED`(409), `DUPLICATE_OPEN_VOTE_FOR_RANGE`(409) |
| Revision/Regeneration | `RevisionErrorCode` | `REGENERATION_ALREADY_IN_PROGRESS`(409) |

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
| `VOTE_OPENED` | trip topic | WP-E 예약 |
| `VOTE_CLOSED` | trip topic | WP-E 예약 |
| `ITINERARY_REVISION_APPLIED` | trip topic | WP-E 예약 — payload는 신호만, client는 REST로 재조회 |

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

## 7. Frontend API 모듈 경계

`frontend/src/api/`:

- `trips.ts` — 기존, 변경 없음
- `tripWorkspace.ts` — 워크스페이스가 쓰는 기존 호출의 얇은 재노출
- `routes.ts` — DAY 자동차 경로 query와 DTO를 구현했다. 후보 detour·개인 preview는 아직 타입/계약 단계다.
- `membership.ts`, `chat.ts` — 각 work package 구현 상태를 따른다.
- `votes.ts`, `revisions.ts` — 후속 WP-E가 계약과 fetch를 확정한다.
