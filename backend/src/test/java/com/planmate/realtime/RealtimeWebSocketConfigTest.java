package com.planmate.realtime;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

class RealtimeWebSocketConfigTest {

    private final RealtimeStompChannelInterceptor interceptor = mock(RealtimeStompChannelInterceptor.class);
    private final RealtimeWebSocketConfig config = new RealtimeWebSocketConfig(
            List.of("http://localhost:5173"),
            interceptor
    );

    @Test
    void registerStompEndpointsKeepsEventsEndpointAndAllowedOrigins() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        given(registry.addEndpoint("/ws/events")).willReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws/events");
        verify(registration).setAllowedOriginPatterns("http://localhost:5173");
    }

    @Test
    void configureMessageBrokerKeepsTopicBrokerAndApplicationPrefix() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic");
        verify(registry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void configureClientInboundChannelRegistersStompInterceptor() {
        ChannelRegistration registration = mock(ChannelRegistration.class);

        config.configureClientInboundChannel(registration);

        verify(registration).interceptors(interceptor);
    }
}
