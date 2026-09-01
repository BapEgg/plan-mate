package com.planmate.chat.repository;

import com.planmate.chat.entity.ChatMessageEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    Optional<ChatMessageEntity> findByTripIdAndClientMessageId(Long tripId, String clientMessageId);

    List<ChatMessageEntity> findByTripIdOrderByIdDesc(Long tripId, Pageable pageable);

    List<ChatMessageEntity> findByTripIdAndIdLessThanOrderByIdDesc(Long tripId, Long cursor, Pageable pageable);

    List<ChatMessageEntity> findByTripIdAndIdGreaterThanOrderByIdAsc(Long tripId, Long sinceId, Pageable pageable);
}
