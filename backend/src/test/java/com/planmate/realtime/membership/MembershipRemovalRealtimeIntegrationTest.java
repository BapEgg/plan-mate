package com.planmate.realtime.membership;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.realtime.RealtimeSessionRegistry;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * WP-B exit gate: OWNER가 MEMBER를 REST로 내보내면 그 MEMBER의 이미 연결된 STOMP session이
 * 실제로 즉시 끊긴다 — {@code RealtimeSessionRevocationService}를 직접 호출하는 WP-A 테스트와
 * 달리, 실제 프로덕션 경로(컨트롤러 → 서비스 → event → AFTER_COMMIT subscriber)를 전부 통과한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MembershipRemovalRealtimeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

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

    @Test
    void removingAMemberOverRestDisconnectsTheirLiveStompSession() throws Exception {
        Instant now = Instant.now();
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = userRepository.save(UserEntity.createOauthUser(
                "remove-owner-" + suffix + "@example.com", "remove-owner-" + suffix + "@example.com",
                "remove-owner", true, now
        ));
        UserEntity member = userRepository.save(UserEntity.createOauthUser(
                "remove-member-" + suffix + "@example.com", "remove-member-" + suffix + "@example.com",
                "remove-member", true, now
        ));
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Removal realtime trip", "Busan", "place-busan", "Busan, Korea",
                35.1, 129.0, 35.0, 128.9, 35.2, 129.1,
                List.of("locality"), "locality",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                owner, now
        ));
        tripMemberRepository.save(TripMemberEntity.owner(trip, owner, now));
        tripMemberRepository.save(TripMemberEntity.member(trip, member, now));
        String ownerToken = jwtTokenProvider.issueAccessToken(owner.getId(), UserRole.USER, now).value();
        String memberToken = jwtTokenProvider.issueAccessToken(member.getId(), UserRole.USER, now).value();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        CompletableFuture<Void> disconnected = new CompletableFuture<>();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken);

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

        awaitSessionRegistered(trip.getId(), member.getId());

        HttpHeaders removeHeaders = new HttpHeaders();
        removeHeaders.setBearerAuth(ownerToken);
        ResponseEntity<Void> removeResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/trips/" + trip.getId() + "/members/" + member.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(removeHeaders),
                Void.class
        );
        assertThat(removeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        disconnected.get(5, TimeUnit.SECONDS);
        assertThat(sessionRegistry.findSessionIds(trip.getId(), member.getId())).isEmpty();

        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(memberToken);
        ResponseEntity<String> tripAfterRemoval = restTemplate.exchange(
                "http://localhost:" + port + "/api/trips/" + trip.getId(),
                HttpMethod.GET,
                new HttpEntity<>(getHeaders),
                String.class
        );
        assertThat(tripAfterRemoval.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
