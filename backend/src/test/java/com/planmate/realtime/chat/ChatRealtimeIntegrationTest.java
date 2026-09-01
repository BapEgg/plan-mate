package com.planmate.realtime.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * WP-D phase 1 exit slice: 두 계정이 같은 trip topic을 구독한 상태에서 한 쪽이 REST로 메시지를
 * 보내면, 보낸 사람 자신을 포함해 둘 다 {@code CHAT_MESSAGE_SENT}를 실시간으로 받는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatRealtimeIntegrationTest {

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
    private ObjectMapper objectMapper;

    @Test
    void bothSubscribedMembersReceiveANewlySentMessageOverTheTripTopic() throws Exception {
        Instant now = Instant.now();
        String suffix = UUID.randomUUID().toString();
        UserEntity sender = userRepository.save(UserEntity.createOauthUser(
                "chat-sender-" + suffix + "@example.com", "chat-sender-" + suffix + "@example.com",
                "chat-sender", true, now
        ));
        UserEntity listener = userRepository.save(UserEntity.createOauthUser(
                "chat-listener-" + suffix + "@example.com", "chat-listener-" + suffix + "@example.com",
                "chat-listener", true, now
        ));
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Chat realtime trip", "Busan", "place-busan", "Busan, Korea",
                35.1, 129.0, 35.0, 128.9, 35.2, 129.1,
                List.of("locality"), "locality",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                sender, now
        ));
        tripMemberRepository.save(TripMemberEntity.owner(trip, sender, now));
        tripMemberRepository.save(TripMemberEntity.member(trip, listener, now));
        String senderToken = jwtTokenProvider.issueAccessToken(sender.getId(), UserRole.USER, now).value();
        String listenerToken = jwtTokenProvider.issueAccessToken(listener.getId(), UserRole.USER, now).value();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        CompletableFuture<ChatMessageSentPayload> senderReceived = new CompletableFuture<>();
        CompletableFuture<ChatMessageSentPayload> listenerReceived = new CompletableFuture<>();

        connectAndSubscribe(stompClient, senderToken, trip.getId(), senderReceived);
        connectAndSubscribe(stompClient, listenerToken, trip.getId(), listenerReceived);

        String clientMessageId = UUID.randomUUID().toString();
        HttpHeaders sendHeaders = new HttpHeaders();
        sendHeaders.setBearerAuth(senderToken);
        sendHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> sendResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/trips/" + trip.getId() + "/chat/messages",
                HttpMethod.POST,
                new HttpEntity<>("{\"clientMessageId\":\"" + clientMessageId + "\",\"body\":\"실시간으로 잘 오나요\"}", sendHeaders),
                String.class
        );
        assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ChatMessageSentPayload senderPayload = senderReceived.get(5, TimeUnit.SECONDS);
        ChatMessageSentPayload listenerPayload = listenerReceived.get(5, TimeUnit.SECONDS);

        assertThat(senderPayload.clientMessageId()).isEqualTo(clientMessageId);
        assertThat(senderPayload.body()).isEqualTo("실시간으로 잘 오나요");
        assertThat(listenerPayload.clientMessageId()).isEqualTo(clientMessageId);
        assertThat(listenerPayload.authorUserId()).isEqualTo(sender.getId());
    }

    private void connectAndSubscribe(
            WebSocketStompClient stompClient,
            String token,
            Long tripId,
            CompletableFuture<ChatMessageSentPayload> received
    ) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/events",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        ).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/trips/" + tripId + "/events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    ChatEventEnvelope envelope = objectMapper.readValue((byte[]) payload, ChatEventEnvelope.class);
                    if ("CHAT_MESSAGE_SENT".equals(envelope.type())) {
                        received.complete(envelope.payload());
                    }
                } catch (Exception exception) {
                    received.completeExceptionally(exception);
                }
            }
        });

        awaitSubscribed();
    }

    private void awaitSubscribed() throws InterruptedException {
        // No direct hook for "subscription is registered server-side" here (unlike the membership
        // test, which asserts against RealtimeSessionRegistry) — a short settle delay is enough
        // since STOMP CONNECT/SUBSCRIBE complete before connectAsync's future resolves.
        Thread.sleep(200);
    }

    private record ChatEventEnvelope(String type, ChatMessageSentPayload payload) {
    }
}
