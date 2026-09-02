package com.planmate.chat.repository;

import com.planmate.chat.entity.ChatMessageReactionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageReactionRepository extends JpaRepository<ChatMessageReactionEntity, Long> {

    Optional<ChatMessageReactionEntity> findByMessage_IdAndUser_Id(Long messageId, Long userId);

    @EntityGraph(attributePaths = {"user", "message"})
    List<ChatMessageReactionEntity> findByMessage_IdIn(Collection<Long> messageIds);

    void deleteByMessage_Id(Long messageId);
}
