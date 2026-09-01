# PlanMate 협업 워크스페이스 API·이벤트 계약

> 상태: WP-A Foundation 완료 시점 기준 고정
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
| Route | `RouteErrorCode` | `ROUTE_QUOTA_EXCEEDED`(429), `ROUTE_TIMEOUT`(504 또는 `ROUTE_PROVIDER_UNAVAILABLE` 재사용) |
| Chat | `ChatErrorCode` | `DUPLICATE_CLIENT_MESSAGE_ID`(200/idempotent replay, 예외 아님), `MESSAGE_EDIT_WINDOW_EXPIRED`(409) |
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
| `CHAT_MESSAGE_SENT` | trip topic | WP-D 예약 |
| `CHAT_UNREAD_CHANGED` | 개인 | WP-D 예약 |
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

## 7. Frontend API 모듈 경계

`frontend/src/api/`:

- `trips.ts` — 기존, 변경 없음
- `tripWorkspace.ts` — 워크스페이스가 쓰는 기존 호출의 얇은 재노출
- `membership.ts`, `routes.ts`, `chat.ts`, `votes.ts`, `revisions.ts` — WP-A는 §6 표에 대응하는 **타입만** 정의한 stub(실제 fetch 없음). WP-B~F는 이 타입을 재사용하고, 새 필드가 필요하면 이 문서와 타입을 함께 갱신한다.
