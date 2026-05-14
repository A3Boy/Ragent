package com.jawn.ragent.service;

import com.jawn.ragent.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DocumentService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final DocumentRepository documentRepository;

    public DocumentService(VectorStore vectorStore, DocumentRepository documentRepository) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.textSplitter = new TokenTextSplitter(500, 100, 50, 1000, true);
    }

    /**
     * 上传并处理文档（归属到指定知识库）
     */
    public String uploadDocument(MultipartFile file, String documentName, String knowledgeBaseId) throws IOException {
        String documentId = UUID.randomUUID().toString();
        String name = (documentName != null && !documentName.isBlank())
                ? documentName
                : file.getOriginalFilename();

        log.info("开始处理文档: {}, ID: {}, 知识库: {}, 大小: {} bytes",
                name, documentId, knowledgeBaseId, file.getSize());

        InputStreamResource resource = new InputStreamResource(file.getInputStream());
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();

        if (documents.isEmpty()) {
            throw new IllegalArgumentException("无法解析文档内容: " + name);
        }

        log.info("文档解析完成，共 {} 页/段", documents.size());

        List<Document> chunks = textSplitter.split(documents);
        log.info("文本分块完成，共 {} 个片段", chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("document_id", documentId);
            metadata.put("document_name", name);
            metadata.put("knowledge_base_id", knowledgeBaseId);
            metadata.put("chunk_index", i);
            metadata.put("total_chunks", chunks.size());
            metadata.put("file_type", file.getContentType());
            metadata.put("upload_time", System.currentTimeMillis());

            chunks.set(i, new Document(chunk.getText(), metadata));
        }

        int batchSize = 10;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, chunks.size());
            List<Document> batchChunks = chunks.subList(i, endIndex);
            vectorStore.add(batchChunks);
            log.info("已向量化并存储片段: {} 到 {}", i + 1, endIndex);
        }

        log.info("文档向量化并存储完成: {}", documentId);

        // 保存文档元数据到数据库
        com.jawn.ragent.entity.Document docEntity = com.jawn.ragent.entity.Document.builder()
                .id(documentId)
                .filename(name)
                .knowledgeBaseId(knowledgeBaseId)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .build();
        documentRepository.save(docEntity);

        return documentId;
    }

    /**
     * 删除文档及其所有向量（校验知识库归属）
     */
    public void deleteDocument(String documentId, String knowledgeBaseId) {
        log.info("删除文档: {}, 知识库: {}", documentId, knowledgeBaseId);
        vectorStore.delete("document_id == '" + documentId + "' && knowledge_base_id == '" + knowledgeBaseId + "'");
        documentRepository.deleteById(documentId);
    }

    /**
     * 在指定知识库内搜索相似片段
     */
    public List<Document> searchInKnowledgeBase(String query, String knowledgeBaseId, int topK) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("knowledge_base_id == '" + knowledgeBaseId + "'")
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    /**
     * 在指定知识库的指定文档内搜索相似片段
     */
    public List<Document> searchInDocument(String query, String documentId, String knowledgeBaseId, int topK) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("document_id == '" + documentId + "' && knowledge_base_id == '" + knowledgeBaseId + "'")
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }
}
