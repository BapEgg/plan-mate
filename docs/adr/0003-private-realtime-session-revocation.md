# ADR-0003: Private realtime destination and session revocation

- 상태: 채택
- 결정일: 2026-08-31
- 관련 spec: `docs/collaboration-workspace-spec.md` §4.1, §4.4, §10.1 (C3), §10.5
- 소비 package: WP-B (내보내기 즉시 revocation 호출), WP-D (invite inbox/chat ack/unread private destination)

## Context

`/ws/events`는 STOMP endpoint 하나이며 broker는 `/topic`만 활성화되어 있다.
`RealtimeStompChannelInterceptor`는 `CONNECT` 시 JWT를 검사하고 `SUBSCRIBE` 시 멤버십을
검사하지만, 이미 성공적으로 구독한 session은 이후 멤버십이 사라져도 계속 event를 받는다 —
spec §3.2/§10.5가 지적하는 정확한 gap이다. 또한 초대함·채팅 ack·개인 unread처럼 다른 참여자에게
노출되면 안 되는 payload를 보낼 개인 destination이 아직 없다.

## Decision

### 1. Private destination

broker에 `/queue`를 추가로 활성화해(`enableSimpleBroker("/topic", "/queue")`) Spring의
`/user/**` 변환이 동작하게 한다. `PrivateRealtimeEventPublisher` port를 추가한다:

```java
void sendToUser(Long userId, Long tripId, String eventType, Object payload);
```

구현은 `SimpMessagingTemplate.convertAndSendToUser(userId.toString(), "/queue/trips/" + tripId + "/events", envelope)`를
감싼다. WP-A는 이 port를 배선만 하고 실제로 호출하는 곳은 만들지 않는다 — 초대함/채팅 ack가
아직 없기 때문이다. WP-B/D가 개인 payload(초대, 읽음, unread count)를 여기로 보낸다.

### 2. Session registry

`RealtimeSessionRegistry`(in-memory `ConcurrentHashMap<String sessionId, SessionState>`,
`SessionState = {Long userId, Set<Long> tripIds}`)를 추가한다. 기존
`RealtimeStompChannelInterceptor`를 확장해:

- `SUBSCRIBE`가 멤버십 검사를 통과하면 registry에 `(sessionId, userId, tripId)`를 기록한다.
- `DISCONNECT` 및 `SessionDisconnectEvent`(비정상 종료 포함)에서 해당 sessionId 항목을
  제거한다.

### 3. Forced disconnect

`RealtimeSessionRevocationService.revokeTripAccess(Long tripId, Long userId)`는 registry에서
`(tripId, userId)`와 일치하는 모든 session을 찾아 각 session에 대해 `clientOutboundChannel`로
합성 STOMP `DISCONNECT` command message를 보낸다(`SimpMessageHeaderAccessor`에
`sessionId` 헤더를 세팅해 특정 session만 종료하는, Spring에 문서화된 강제 종료 기법). 클라이언트의
기존 `reconnectDelay`(3000ms, `frontend/src/api/realtime.ts`)가 재연결을 시도하고, 이미
구현된 SUBSCRIBE-time 멤버십 검사가 재구독을 거부한다 — 그 결과 새 event가 더 이상 전달되지
않는다.

WP-A는 이 서비스를 구현하고 실제 STOMP 클라이언트로 "구독 → revoke 호출 → 연결 종료 확인"을
증명하는 통합 테스트만 추가한다. 호출자(멤버 제거 command)는 WP-B가 구현한다.

## Rejected alternatives

**Redis 기반 cross-instance session registry.** 지금은 단일 instance 배포이고(ADR-0004),
multi-instance가 실제 요구사항이 되기 전에 분산 registry를 만들 이유가 없다. in-memory
구현은 인터페이스 뒤에 있어 나중에 교체 가능하다.

**client-side self-disconnect만 신뢰(서버가 payload만 보내고 클라이언트가 알아서 끊기).**
spec §4.1이 명시적으로 "이미 연결된 WebSocket도... 새 event를 받지 못해야 한다"고 요구한다.
클라이언트를 신뢰하지 않고 서버가 강제로 session을 끊는 방식만 이 요구를 만족한다.

## Backfill / migration

스키마 변경 없음(in-memory 상태만 추가). Rollback은 코드 되돌리기로 충분하다.

## Concurrency

registry는 `ConcurrentHashMap`으로 session 등록/해제가 SUBSCRIBE/DISCONNECT 이벤트 스레드에서
동시에 일어나도 안전하다. revoke 호출은 registry snapshot을 순회하며 개별 실패(이미 끊긴
session)를 무시하고 계속 진행한다.

## Tests

- 실제 `@LocalServerPort`로 STOMP client를 띄워 구독 → `revokeTripAccess` 호출 → 클라이언트
  disconnect 관찰까지 검증하는 통합 테스트
- `RealtimeSessionRegistry` 등록/해제 unit test (SUBSCRIBE 성공 시에만 등록, DISCONNECT 시
  제거)
- 존재하지 않는 session에 대한 revoke가 예외 없이 no-op임을 확인하는 test
