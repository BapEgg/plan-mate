package com.planmate.chat.repository;

import com.planmate.chat.entity.ChatMessageEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    Optional<ChatMessageEntity> findByTripIdAndClientMessageId(Long tripId, String clientMessageId);

    List<ChatMessageEntity> findByTripIdAndSentAtAfterOrderByIdDesc(Long tripId, Instant intervalStart, Pageable pageable);

    List<ChatMessageEntity> findByTripIdAndIdLessThanAndSentAtAfterOrderByIdDesc(
            Long tripId, Long cursor, Instant intervalStart, Pageable pageable
    );

    List<ChatMessageEntity> findByTripIdAndIdGreaterThanAndSentAtAfterOrderByIdAsc(
            Long tripId, Long sinceId, Instant intervalStart, Pageable pageable
    );

    /** spec §4 "읽음과 visibility": 내 message와 현재 membership interval 이전 message는 unread에서 뺀다. */
    @Query("""
            SELECT COUNT(m) FROM ChatMessageEntity m
            WHERE m.tripId = :tripId
              AND (m.authorUserId IS NULL OR m.authorUserId <> :userId)
              AND m.sentAt > :intervalStart
              AND (:lastReadId IS NULL OR m.id > :lastReadId)
            """)
    long countUnread(
            @Param("tripId") Long tripId,
            @Param("userId") Long userId,
            @Param("intervalStart") Instant intervalStart,
            @Param("lastReadId") Long lastReadId
    );
}
