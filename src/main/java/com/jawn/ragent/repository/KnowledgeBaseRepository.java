package com.jawn.ragent.repository;

import com.jawn.ragent.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, String> {

    List<KnowledgeBase> findByUserId(String userId);

    Optional<KnowledgeBase> findByIdAndUserId(String id, String userId);
}
