package com.planmate.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.domain.UserRole;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * ADR-0003: 실제 STOMP-over-WebSocket 연결로 "구독 → 멤버십 상실(revoke) → session
 * disconnect"를 end-to-end로 증명한다. 이미 연결된 client가 새 event를 받지 못하게 되는
 * 근거는 "session이 서버에 의해 강제로 끊긴다"는 사실이다 — 재연결 뒤에는 기존 SUBSCRIBE-time
 * 멤버십 검사가 다시 거부한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeSessionRevocationServiceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RealtimeSessionRegistry sessionRegistry;

    @Autowired
    private RealtimeSessionRevocationService revocationService;

    @Test
    void revokeTripAccessDisconnectsSubscribedSession() throws Exception {
        Instant now = Instant.now();
        String uniqueSuffix = UUID.randomUUID().toString();
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "revocation-" + uniqueSuffix + "@example.com",
                "revocation-" + uniqueSuffix + "@example.com",
                "revocation-user",
                true,
                now
        ));
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Revocation trip",
                "Busan",
                "place-busan",
                "Busan, Korea",
                35.1, 129.0,
                35.0, 128.9,
                35.2, 129.1,
                List.of("locality"),
                "locality",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                user,
                now
        ));
        tripMemberRepository.save(TripMemberEntity.member(trip, user, now));
        String token = jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, now).value();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        CompletableFuture<Void> disconnected = new CompletableFuture<>();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/events",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        disconnected.complete(null);
                    }
                }
        ).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/trips/" + trip.getId() + "/events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        });

        awaitSessionRegistered(trip.getId(), user.getId());

        revocationService.revokeTripAccess(trip.getId(), user.getId());

        disconnected.get(5, TimeUnit.SECONDS);
        assertThat(sessionRegistry.findSessionIds(trip.getId(), user.getId())).isEmpty();
    }

    private void awaitSessionRegistered(Long tripId, Long userId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (!sessionRegistry.findSessionIds(tripId, userId).isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Subscription was never registered in RealtimeSessionRegistry");
    }
}
