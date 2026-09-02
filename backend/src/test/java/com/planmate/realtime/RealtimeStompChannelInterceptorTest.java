package com.planmate.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.auth.security.PlanMateJwtAuthenticationConverter;
import com.planmate.trip.api.TripMembershipChecker;
import com.planmate.realtime.presence.PresenceService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@ExtendWith(MockitoExtension.class)
class RealtimeStompChannelInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private TripMembershipChecker tripMembershipChecker;

    @Mock
    private PresenceService presenceService;
    private RealtimeStompChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RealtimeStompChannelInterceptor(
                jwtDecoder,
                new PlanMateJwtAuthenticationConverter(),
                tripMembershipChecker,
                presenceService
        );
    }

    @Test
    void connectAuthenticatesBearerTokenAndAttachesPrincipal() {
        given(jwtDecoder.decode("valid-token")).willReturn(jwt(7L));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

        interceptor.preSend(message(accessor), mock(MessageChannel.class));

        assertThat(accessor.getUser())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class)
                .extracting(user -> ((UsernamePasswordAuthenticationToken) user).getPrincipal())
                .isEqualTo(new AuthenticatedUser(7L, "USER"));
    }

    @Test
    void connectRejectsInvalidToken() {
        given(jwtDecoder.decode("bad-token")).willThrow(new BadJwtException("bad token"));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectRejectsMissingToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void subscribeAllowsTripMember() {
        given(tripMembershipChecker.isMember(7L, 45L)).willReturn(true);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/trips/45/events");
        accessor.setUser(authentication(7L));

        Message<?> result = interceptor.preSend(message(accessor), mock(MessageChannel.class));

        assertThat(result).isNotNull();
        verify(tripMembershipChecker).isMember(7L, 45L);
    }

    @Test
    void subscribeRejectsNonMember() {
        given(tripMembershipChecker.isMember(7L, 45L)).willReturn(false);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/trips/45/events");
        accessor.setUser(authentication(7L));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeRejectsUnsupportedTopicPath() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/unknown");
        accessor.setUser(authentication(7L));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Unsupported topic subscription");
    }

    @Test
    void subscribeAllowsNonTopicDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/queue/private");
        accessor.setUser(authentication(7L));

        Message<?> result = interceptor.preSend(message(accessor), mock(MessageChannel.class));

        assertThat(result).isNotNull();
    }

    @Test
    void sendToTopicIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/trips/45/events");
        accessor.setUser(authentication(7L));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeRegistersSessionForRevocation() {
        given(tripMembershipChecker.isMember(7L, 45L)).willReturn(true);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-1");
        accessor.setDestination("/topic/trips/45/events");
        accessor.setUser(authentication(7L));

        interceptor.preSend(message(accessor), mock(MessageChannel.class));

        verify(presenceService).register("session-1", 7L, 45L);
    }

    @Test
    void disconnectRemovesSessionFromRegistry() {
        given(tripMembershipChecker.isMember(7L, 45L)).willReturn(true);
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setSessionId("session-1");
        subscribe.setDestination("/topic/trips/45/events");
        subscribe.setUser(authentication(7L));
        interceptor.preSend(message(subscribe), mock(MessageChannel.class));

        StompHeaderAccessor disconnect = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        disconnect.setSessionId("session-1");
        interceptor.preSend(message(disconnect), mock(MessageChannel.class));

        verify(presenceService).disconnectImmediately("session-1");
    }

    private Jwt jwt(Long userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("role", "USER")
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(900))
                .build();
    }

    private UsernamePasswordAuthenticationToken authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "USER"),
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
