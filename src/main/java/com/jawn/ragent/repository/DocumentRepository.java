package com.jawn.ragent.repository;

import com.jawn.ragent.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByKnowledgeBaseIdOrderByCreatedAtDesc(String knowledgeBaseId);

    Optional<Document> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);

    void deleteByKnowledgeBaseId(String knowledgeBaseId);
}
