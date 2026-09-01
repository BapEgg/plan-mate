package com.planmate.chat.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.chat.dto.ChatHistoryResponse;
import com.planmate.chat.dto.ChatMessageResponse;
import com.planmate.chat.dto.SendChatMessageRequest;
import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.service.ChatHistoryService;
import com.planmate.chat.service.ChatMessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/chat/messages")
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatHistoryService chatHistoryService;

    public ChatController(ChatMessageService chatMessageService, ChatHistoryService chatHistoryService) {
        this.chatMessageService = chatMessageService;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping
    public ResponseEntity<ChatMessageResponse> send(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @RequestBody SendChatMessageRequest request
    ) {
        ChatMessageService.Result result = chatMessageService.send(
                user.userId(), tripId, request.clientMessageId(), request.body()
        );
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ChatMessageResponse.from(result.message()));
    }

    @GetMapping
    public ChatHistoryResponse history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long since
    ) {
        if (since != null) {
            return new ChatHistoryResponse(
                    chatHistoryService.listSince(user.userId(), tripId, since).stream().map(ChatMessageResponse::from).toList(),
                    null
            );
        }
        ChatHistoryService.Page page = chatHistoryService.listHistory(user.userId(), tripId, cursor, size);
        return new ChatHistoryResponse(
                page.messages().stream().map(ChatMessageResponse::from).toList(),
                page.nextCursor() == null ? null : page.nextCursor().toString()
        );
    }

    @GetMapping("/by-client-id/{clientMessageId}")
    public ChatMessageResponse byClientMessageId(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable String clientMessageId
    ) {
        ChatMessageEntity message = chatMessageService.findByClientMessageId(user.userId(), tripId, clientMessageId);
        return ChatMessageResponse.from(message);
    }
}
