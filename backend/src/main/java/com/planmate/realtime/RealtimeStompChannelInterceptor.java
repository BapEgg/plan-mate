package com.planmate.realtime;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.auth.security.PlanMateJwtAuthenticationConverter;
import com.planmate.trip.api.TripMembershipChecker;
import com.planmate.realtime.presence.PresenceService;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class RealtimeStompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern TRIP_EVENTS_DESTINATION = Pattern.compile("^/topic/trips/(\\d+)/events$");

    private final JwtDecoder jwtDecoder;
    private final PlanMateJwtAuthenticationConverter jwtAuthenticationConverter;
    private final TripMembershipChecker tripMembershipChecker;
    private final PresenceService presenceService;

    public RealtimeStompChannelInterceptor(
            JwtDecoder jwtDecoder,
            PlanMateJwtAuthenticationConverter jwtAuthenticationConverter,
            TripMembershipChecker tripMembershipChecker,
            PresenceService presenceService
    ) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.tripMembershipChecker = tripMembershipChecker;
        this.presenceService = presenceService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
        }
        if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(accessor);
        }
        if (StompCommand.SEND.equals(command)) {
            rejectBrokerTopicSend(accessor);
        }
        if (StompCommand.DISCONNECT.equals(command)) {
            presenceService.disconnectImmediately(accessor.getSessionId());
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AuthenticationCredentialsNotFoundException("STOMP CONNECT requires bearer token");
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new AuthenticationCredentialsNotFoundException("STOMP CONNECT requires bearer token");
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            Authentication authentication = jwtAuthenticationConverter.convert(jwt);
            accessor.setUser(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid STOMP bearer token", exception);
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination) || !destination.startsWith("/topic/")) {
            return;
        }

        Matcher matcher = TRIP_EVENTS_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            throw new AccessDeniedException("Unsupported topic subscription");
        }

        AuthenticatedUser user = authenticatedUser(accessor.getUser());
        Long tripId = Long.valueOf(matcher.group(1));
        if (!tripMembershipChecker.isMember(user.userId(), tripId)) {
            throw new AccessDeniedException("Trip membership is required to subscribe");
        }
        presenceService.register(accessor.getSessionId(), user.userId(), tripId);
    }

    private void rejectBrokerTopicSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (StringUtils.hasText(destination) && destination.startsWith("/topic/")) {
            throw new AccessDeniedException("Clients cannot send directly to broker topics");
        }
    }

    /**
     * 정상 STOMP DISCONNECT 없이 끊기는 경우(네트워크 단절, 브라우저 종료 등)에도 registry가
     * 정확하게 유지되도록, transport 레벨 종료 이벤트에서도 session을 제거한다.
     */
    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        presenceService.disconnectWithGrace(event.getSessionId());
    }

    private AuthenticatedUser authenticatedUser(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AuthenticationCredentialsNotFoundException("Authenticated STOMP user is required");
    }
}
