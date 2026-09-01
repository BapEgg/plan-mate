package com.planmate.chat.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.chat.dto.ChatUnreadCountResponse;
import com.planmate.chat.dto.MarkChatReadRequest;
import com.planmate.chat.service.ChatUnreadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/chat")
public class ChatReadController {

    private final ChatUnreadService chatUnreadService;

    public ChatReadController(ChatUnreadService chatUnreadService) {
        this.chatUnreadService = chatUnreadService;
    }

    @GetMapping("/unread-count")
    public ChatUnreadCountResponse unreadCount(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        return new ChatUnreadCountResponse(chatUnreadService.getUnreadCount(user.userId(), tripId));
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @RequestBody MarkChatReadRequest request
    ) {
        chatUnreadService.markRead(user.userId(), tripId, request.messageId());
        return ResponseEntity.noContent().build();
    }
}
