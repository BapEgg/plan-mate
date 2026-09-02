package com.planmate.chat.service;

import com.planmate.chat.dto.ChatMessageResponse;
import com.planmate.chat.dto.ChatMentionResponse;
import com.planmate.chat.dto.ChatReactionSummaryResponse;
import com.planmate.chat.dto.ChatReplyPreviewResponse;
import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.entity.ChatMessageReactionEntity;
import com.planmate.chat.entity.ChatMessageMentionEntity;
import com.planmate.chat.entity.ChatReactionType;
import com.planmate.chat.repository.ChatMessageReactionRepository;
import com.planmate.chat.repository.ChatMessageMentionRepository;
import com.planmate.chat.repository.ChatMessageRepository;
import com.planmate.trip.entity.MembershipStatus;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.repository.TripMemberRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChatMessageResponseAssembler {

    private static final int REPLY_PREVIEW_LENGTH = 80;

    private final ChatMessageRepository messageRepository;
    private final ChatMessageReactionRepository reactionRepository;
    private final ChatMessageMentionRepository mentionRepository;
    private final TripMemberRepository tripMemberRepository;

    public ChatMessageResponseAssembler(
            ChatMessageRepository messageRepository,
            ChatMessageReactionRepository reactionRepository,
            ChatMessageMentionRepository mentionRepository,
            TripMemberRepository tripMemberRepository
    ) {
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.mentionRepository = mentionRepository;
        this.tripMemberRepository = tripMemberRepository;
    }

    @Transactional(readOnly = true)
    public ChatMessageResponse assemble(Long viewerUserId, ChatMessageEntity message) {
        return assembleAll(viewerUserId, List.of(message)).get(0);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> assembleAll(Long viewerUserId, List<ChatMessageEntity> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        Map<Long, ChatMessageEntity> replies = messageRepository.findAllById(
                        messages.stream()
                                .map(ChatMessageEntity::getReplyToMessageId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList()
                ).stream()
                .collect(Collectors.toMap(ChatMessageEntity::getId, Function.identity()));

        List<Long> messageIds = messages.stream().map(ChatMessageEntity::getId).toList();
        Map<Long, List<ChatMessageReactionEntity>> reactionsByMessage = reactionRepository
                .findByMessage_IdIn(messageIds)
                .stream()
                .collect(Collectors.groupingBy(reaction -> reaction.getMessage().getId()));
        Map<Long, List<ChatMessageMentionEntity>> mentionsByMessage = mentionRepository
                .findByMessage_IdIn(messageIds)
                .stream()
                .collect(Collectors.groupingBy(mention -> mention.getMessage().getId()));

        Long tripId = messages.get(0).getTripId();
        Map<Long, String> activeMemberNames = tripMemberRepository
                .findByTrip_IdAndStatusOrderByCreatedAtAsc(tripId, MembershipStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        member -> member.getUser().getId(),
                        member -> member.getUser().getNickname(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return messages.stream()
                .map(message -> assembleOne(
                        viewerUserId,
                        message,
                        replies.get(message.getReplyToMessageId()),
                        reactionsByMessage.getOrDefault(message.getId(), List.of()),
                        mentionsByMessage.getOrDefault(message.getId(), List.of()),
                        activeMemberNames
                ))
                .toList();
    }

    private ChatMessageResponse assembleOne(
            Long viewerUserId,
            ChatMessageEntity message,
            ChatMessageEntity reply,
            List<ChatMessageReactionEntity> reactions,
            List<ChatMessageMentionEntity> mentions,
            Map<Long, String> activeMemberNames
    ) {
        return new ChatMessageResponse(
                message.getId(),
                message.getTripId().toString(),
                message.getAuthorUserId(),
                message.getType(),
                message.isDeleted() ? "삭제된 메시지입니다." : message.getBody(),
                message.getClientMessageId(),
                message.getSentAt(),
                replyPreview(reply),
                message.isDeleted(),
                message.getDeletedAt(),
                message.getSentAt().plusSeconds(300),
                message.isDeleted() ? List.of() : reactionSummaries(viewerUserId, reactions, activeMemberNames),
                message.isDeleted() ? List.of() : mentions.stream()
                        .map(mention -> new ChatMentionResponse(
                                mention.getMentionedUser().getId(),
                                mention.getDisplayNameSnapshot(),
                                mention.getStartCodePoint(),
                                mention.getEndCodePoint()
                        ))
                        .toList()
        );
    }

    private ChatReplyPreviewResponse replyPreview(ChatMessageEntity reply) {
        if (reply == null) {
            return null;
        }
        String body = reply.isDeleted() ? "삭제된 메시지입니다." : compact(reply.getBody());
        return new ChatReplyPreviewResponse(reply.getId(), reply.getAuthorUserId(), body, reply.isDeleted());
    }

    private List<ChatReactionSummaryResponse> reactionSummaries(
            Long viewerUserId,
            List<ChatMessageReactionEntity> reactions,
            Map<Long, String> activeMemberNames
    ) {
        Map<ChatReactionType, List<ChatMessageReactionEntity>> grouped = new EnumMap<>(ChatReactionType.class);
        reactions.stream()
                .filter(reaction -> activeMemberNames.containsKey(reaction.getUser().getId()))
                .forEach(reaction -> grouped.computeIfAbsent(reaction.getType(), ignored -> new ArrayList<>()).add(reaction));

        List<ChatReactionSummaryResponse> summaries = new ArrayList<>();
        for (ChatReactionType type : ChatReactionType.values()) {
            List<ChatMessageReactionEntity> entries = grouped.getOrDefault(type, List.of());
            if (entries.isEmpty()) {
                continue;
            }
            summaries.add(new ChatReactionSummaryResponse(
                    type,
                    entries.size(),
                    entries.stream().map(entry -> activeMemberNames.get(entry.getUser().getId())).toList(),
                    entries.stream().anyMatch(entry -> entry.getUser().getId().equals(viewerUserId))
            ));
        }
        return summaries;
    }

    private String compact(String body) {
        String oneLine = body.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= REPLY_PREVIEW_LENGTH) {
            return oneLine;
        }
        return oneLine.substring(0, REPLY_PREVIEW_LENGTH - 1) + "…";
    }
}
