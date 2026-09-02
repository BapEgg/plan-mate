package com.planmate.chat.repository;

import com.planmate.chat.entity.ChatMessageMentionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageMentionRepository extends JpaRepository<ChatMessageMentionEntity, Long> {

    List<ChatMessageMentionEntity> findByMessage_IdIn(List<Long> messageIds);

    void deleteByMessage_Id(Long messageId);
}
