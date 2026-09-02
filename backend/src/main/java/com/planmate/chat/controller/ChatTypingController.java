package com.planmate.chat.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.chat.dto.SetChatTypingRequest;
import com.planmate.chat.service.ChatTypingService;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class ChatTypingController {

    private final ChatTypingService chatTypingService;

    public ChatTypingController(ChatTypingService chatTypingService) {
        this.chatTypingService = chatTypingService;
    }

    @MessageMapping("/trips/{tripId}/chat/typing")
    public void setTyping(
            Principal principal,
            @DestinationVariable Long tripId,
            @Payload SetChatTypingRequest request
    ) {
        AuthenticatedUser user = authenticatedUser(principal);
        chatTypingService.set(
                user.userId(), tripId, request.state(), request.clientSessionId(), request.clientEventId()
        );
    }

    private AuthenticatedUser authenticatedUser(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AuthenticationCredentialsNotFoundException("Authenticated STOMP user is required");
    }
}
