package com.jawn.ragent.repository;

import com.jawn.ragent.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByUserIdAndKnowledgeBaseIdOrderByCreatedAtDesc(
            String userId, String knowledgeBaseId, Pageable pageable);

    void deleteByUserIdAndKnowledgeBaseId(String userId, String knowledgeBaseId);

    void deleteByIdAndUserId(String id, String userId);
}
