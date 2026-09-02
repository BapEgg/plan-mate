package com.planmate.realtime;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
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
    private final TaskScheduler realtimeTaskScheduler;

    public RealtimeWebSocketConfig(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
            RealtimeStompChannelInterceptor stompChannelInterceptor,
            @Qualifier("realtimeTaskScheduler") TaskScheduler realtimeTaskScheduler
    ) {
        this.allowedOrigins = allowedOrigins;
        this.stompChannelInterceptor = stompChannelInterceptor;
        this.realtimeTaskScheduler = realtimeTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/events")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // ADR-0003: /queue는 개인 destination(/user/{userId}/queue/...)에 필요하다.
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(realtimeTaskScheduler)
                .setHeartbeatValue(new long[]{10_000, 10_000});
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
    }
}
