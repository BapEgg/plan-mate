package com.planmate.realtime;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class RealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final List<String> allowedOrigins;
    private final RealtimeStompChannelInterceptor stompChannelInterceptor;

    public RealtimeWebSocketConfig(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
            RealtimeStompChannelInterceptor stompChannelInterceptor
    ) {
        this.allowedOrigins = allowedOrigins;
        this.stompChannelInterceptor = stompChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/events")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
    }
}
