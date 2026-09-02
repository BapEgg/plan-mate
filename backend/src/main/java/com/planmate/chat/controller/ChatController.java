package com.planmate.chat.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.chat.dto.ChatHistoryResponse;
import com.planmate.chat.dto.ChatMessageResponse;
import com.planmate.chat.dto.ChatSearchResponse;
import com.planmate.chat.dto.SendChatMessageRequest;
import com.planmate.chat.dto.SetChatReactionRequest;
import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.service.ChatHistoryService;
import com.planmate.chat.service.ChatMessageService;
import com.planmate.chat.service.ChatMessageResponseAssembler;
import com.planmate.chat.service.ChatReactionService;
import com.planmate.chat.service.ChatSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/chat/messages")
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatHistoryService chatHistoryService;
    private final ChatReactionService chatReactionService;
    private final ChatMessageResponseAssembler responseAssembler;
    private final ChatSearchService chatSearchService;

    public ChatController(
            ChatMessageService chatMessageService,
            ChatHistoryService chatHistoryService,
            ChatReactionService chatReactionService,
            ChatMessageResponseAssembler responseAssembler,
            ChatSearchService chatSearchService
    ) {
        this.chatMessageService = chatMessageService;
        this.chatHistoryService = chatHistoryService;
        this.chatReactionService = chatReactionService;
        this.responseAssembler = responseAssembler;
        this.chatSearchService = chatSearchService;
    }

    @PostMapping
    public ResponseEntity<ChatMessageResponse> send(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @RequestBody SendChatMessageRequest request
    ) {
        ChatMessageService.Result result = chatMessageService.send(
                user.userId(), tripId, request.clientMessageId(), request.body(), request.replyToMessageId(), request.mentions()
        );
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(responseAssembler.assemble(user.userId(), result.message()));
    }

    @GetMapping("/search")
    public ChatSearchResponse search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @RequestParam String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return chatSearchService.search(user.userId(), tripId, q, cursor, limit);
    }

    @GetMapping("/{messageId}/context")
    public ChatHistoryResponse context(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long messageId
    ) {
        return new ChatHistoryResponse(
                responseAssembler.assembleAll(
                        user.userId(),
                        chatSearchService.context(user.userId(), tripId, messageId)
                ),
                null
        );
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
                    responseAssembler.assembleAll(
                            user.userId(),
                            chatHistoryService.listSince(user.userId(), tripId, since)
                    ),
                    null
            );
        }
        ChatHistoryService.Page page = chatHistoryService.listHistory(user.userId(), tripId, cursor, size);
        return new ChatHistoryResponse(
                responseAssembler.assembleAll(user.userId(), page.messages()),
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
        return responseAssembler.assemble(user.userId(), message);
    }

    @GetMapping("/{messageId}")
    public ChatMessageResponse byId(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long messageId
    ) {
        ChatMessageEntity message = chatMessageService.findById(user.userId(), tripId, messageId);
        return responseAssembler.assemble(user.userId(), message);
    }

    @DeleteMapping("/{messageId}")
    public ChatMessageResponse delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long messageId
    ) {
        ChatMessageEntity message = chatMessageService.delete(user.userId(), tripId, messageId);
        return responseAssembler.assemble(user.userId(), message);
    }

    @PutMapping("/{messageId}/reaction")
    public ChatMessageResponse setReaction(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long messageId,
            @RequestBody SetChatReactionRequest request
    ) {
        ChatMessageEntity message = chatReactionService.set(user.userId(), tripId, messageId, request.reaction());
        return responseAssembler.assemble(user.userId(), message);
    }

    @DeleteMapping("/{messageId}/reaction")
    public ChatMessageResponse removeReaction(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long messageId
    ) {
        ChatMessageEntity message = chatReactionService.remove(user.userId(), tripId, messageId);
        return responseAssembler.assemble(user.userId(), message);
    }
}
