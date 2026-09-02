package com.planmate.chat.service;

import com.planmate.chat.dto.ChatSearchMatchRangeResponse;
import com.planmate.chat.dto.ChatSearchResponse;
import com.planmate.chat.dto.ChatSearchResultResponse;
import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.entity.ChatMessageType;
import com.planmate.chat.exception.ChatErrorCode;
import com.planmate.chat.exception.ChatException;
import com.planmate.chat.repository.ChatMessageRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripMembershipIntervalReader;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatSearchService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_QUERY_CODE_POINTS = 100;
    private static final int SNIPPET_CODE_POINTS = 120;

    private final TripAccessChecker tripAccessChecker;
    private final TripMembershipIntervalReader intervalReader;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    public ChatSearchService(
            TripAccessChecker tripAccessChecker,
            TripMembershipIntervalReader intervalReader,
            ChatMessageRepository messageRepository,
            UserRepository userRepository
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.intervalReader = intervalReader;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ChatSearchResponse search(Long userId, Long tripId, String rawQuery, String rawCursor, Integer requestedLimit) {
        tripAccessChecker.checkAccessible(userId, tripId);
        String query = normalizeQuery(rawQuery);
        Instant intervalStart = intervalReader.currentIntervalStartedAt(userId, tripId);
        int limit = requestedLimit == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(DEFAULT_PAGE_SIZE, requestedLimit));

        Cursor cursor = rawCursor == null || rawCursor.isBlank() ? null : decodeCursor(rawCursor, query);
        long snapshot = cursor == null
                ? messageRepository.findTopByTripIdAndTypeAndDeletedAtIsNullAndSentAtAfterOrderByIdDesc(
                        tripId, ChatMessageType.USER_TEXT, intervalStart
                ).map(ChatMessageEntity::getId).orElse(0L)
                : cursor.snapshotSequence();

        List<ChatMessageEntity> fetched = cursor == null
                ? messageRepository.searchFirstPage(
                        tripId, intervalStart, snapshot, escapeLike(query), PageRequest.of(0, limit + 1)
                )
                : messageRepository.searchAfterCursor(
                        tripId, intervalStart, snapshot, cursor.lastMessageId(), escapeLike(query), PageRequest.of(0, limit + 1)
                );

        boolean hasMore = fetched.size() > limit;
        List<ChatMessageEntity> page = hasMore ? fetched.subList(0, limit) : fetched;
        Map<Long, String> authorNames = authorNames(page);
        List<ChatSearchResultResponse> results = page.stream()
                .map(message -> result(message, query, authorNames.get(message.getAuthorUserId())))
                .toList();
        String nextCursor = hasMore
                ? encodeCursor(new Cursor(snapshot, page.get(page.size() - 1).getId(), query))
                : null;
        return new ChatSearchResponse(query, results, nextCursor, hasMore, snapshot);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> context(Long userId, Long tripId, Long messageId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        Instant intervalStart = intervalReader.currentIntervalStartedAt(userId, tripId);
        ChatMessageEntity target = messageRepository.findByIdAndTripId(messageId, tripId)
                .filter(message -> message.getSentAt().isAfter(intervalStart))
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
        List<ChatMessageEntity> older = new ArrayList<>(messageRepository
                .findByTripIdAndIdLessThanAndSentAtAfterOrderByIdDesc(
                        tripId, target.getId(), intervalStart, PageRequest.of(0, 10)
                ));
        java.util.Collections.reverse(older);
        List<ChatMessageEntity> context = new ArrayList<>(older);
        context.add(target);
        context.addAll(messageRepository.findByTripIdAndIdGreaterThanAndSentAtAfterOrderByIdAsc(
                tripId, target.getId(), intervalStart, PageRequest.of(0, 10)
        ));
        return context;
    }

    private String normalizeQuery(String rawQuery) {
        String normalized = Normalizer.normalize(Objects.requireNonNullElse(rawQuery, "").trim(), Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 2 || length > MAX_QUERY_CODE_POINTS) {
            throw new ChatException(ChatErrorCode.INVALID_SEARCH_QUERY);
        }
        return normalized;
    }

    private Map<Long, String> authorNames(List<ChatMessageEntity> messages) {
        List<Long> ids = messages.stream()
                .map(ChatMessageEntity::getAuthorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(ids).forEach(user -> names.put(user.getId(), user.getNickname()));
        return names;
    }

    private ChatSearchResultResponse result(ChatMessageEntity message, String query, String authorName) {
        Snippet snippet = snippet(message.getBody(), query);
        return new ChatSearchResultResponse(
                message.getId(),
                message.getId(),
                authorName == null ? "알 수 없는 참여자" : authorName,
                message.getSentAt(),
                snippet.text(),
                List.of(new ChatSearchMatchRangeResponse(snippet.matchStart(), snippet.matchEnd()))
        );
    }

    private Snippet snippet(String body, String query) {
        int[] bodyPoints = body.codePoints().toArray();
        int[] queryPoints = query.toLowerCase(Locale.ROOT).codePoints().toArray();
        int match = find(body.toLowerCase(Locale.ROOT).codePoints().toArray(), queryPoints);
        if (match < 0) {
            return new Snippet(body, 0, 0);
        }
        int start = Math.max(0, match - 35);
        int end = Math.min(bodyPoints.length, Math.max(match + queryPoints.length + 35, start + SNIPPET_CODE_POINTS));
        if (end - start > SNIPPET_CODE_POINTS) {
            end = start + SNIPPET_CODE_POINTS;
        }
        String prefix = start > 0 ? "…" : "";
        String suffix = end < bodyPoints.length ? "…" : "";
        String text = prefix + new String(bodyPoints, start, end - start) + suffix;
        int prefixLength = prefix.isEmpty() ? 0 : 1;
        return new Snippet(
                text,
                match - start + prefixLength,
                match - start + prefixLength + queryPoints.length
        );
    }

    private int find(int[] body, int[] query) {
        outer:
        for (int i = 0; i <= body.length - query.length; i++) {
            for (int j = 0; j < query.length; j++) {
                if (body[i + j] != query[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private String escapeLike(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String encodeCursor(Cursor cursor) {
        String value = cursor.snapshotSequence() + ":" + cursor.lastMessageId() + ":" + cursor.query();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String rawCursor, String query) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 3);
            Cursor cursor = new Cursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]), parts[2]);
            if (!cursor.query().equals(query)) {
                throw new IllegalArgumentException("query mismatch");
            }
            return cursor;
        } catch (RuntimeException exception) {
            throw new ChatException(ChatErrorCode.INVALID_SEARCH_QUERY);
        }
    }

    private record Cursor(long snapshotSequence, long lastMessageId, String query) {
    }

    private record Snippet(String text, int matchStart, int matchEnd) {
    }
}
