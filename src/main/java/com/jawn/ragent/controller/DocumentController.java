package com.jawn.ragent.controller;

import com.jawn.ragent.repository.DocumentRepository;
import com.jawn.ragent.repository.KnowledgeBaseRepository;
import com.jawn.ragent.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@Slf4j
public class DocumentController {

    private final DocumentService documentService;
    private final KnowledgeBaseRepository kbRepository;
    private final DocumentRepository documentRepository;

    public DocumentController(DocumentService documentService, KnowledgeBaseRepository kbRepository,
                              DocumentRepository documentRepository) {
        this.documentService = documentService;
        this.kbRepository = kbRepository;
        this.documentRepository = documentRepository;
    }

    @GetMapping
    public ResponseEntity<?> listDocuments(@RequestAttribute("userId") String userId,
                                            @RequestParam("knowledgeBaseId") String knowledgeBaseId) {
        // 校验知识库归属
        if (kbRepository.findByIdAndUserId(knowledgeBaseId, userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<com.jawn.ragent.entity.Document> docs = documentRepository
                .findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId);

        List<Map<String, Object>> items = docs.stream().map(doc -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", doc.getId());
            item.put("filename", doc.getFilename());
            item.put("knowledgeBaseId", doc.getKnowledgeBaseId());
            item.put("contentType", doc.getContentType());
            item.put("fileSize", doc.getFileSize());
            item.put("createdAt", doc.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("documents", items);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestAttribute("userId") String userId,
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String documentName) {

        log.info("收到文档上传请求: {}, 大小: {} bytes, 知识库: {}", file.getOriginalFilename(), file.getSize(), knowledgeBaseId);

        // 校验知识库归属
        if (kbRepository.findByIdAndUserId(knowledgeBaseId, userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("文件不能为空"));
        }

        if (file.getSize() > 50 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(createErrorResponse("文件大小超过 50MB 限制"));
        }

        try {
            String documentId = documentService.uploadDocument(file, documentName, knowledgeBaseId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documentId", documentId);
            response.put("knowledgeBaseId", knowledgeBaseId);
            response.put("name", documentName != null ? documentName : file.getOriginalFilename());
            response.put("message", "文档上传成功，已进行向量化处理");

            log.info("文档上传成功: {}", documentId);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("文档上传失败", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("文档处理失败: " + e.getMessage()));
        } catch (Exception e) {
            log.error("文档处理异常", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("处理异常: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(
            @RequestAttribute("userId") String userId,
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @PathVariable String documentId) {

        log.info("删除文档请求: {}, 知识库: {}", documentId, knowledgeBaseId);

        // 校验知识库归属
        if (kbRepository.findByIdAndUserId(knowledgeBaseId, userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            documentService.deleteDocument(documentId, knowledgeBaseId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文档删除成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除文档失败", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("删除失败: " + e.getMessage()));
        }
    }

    @GetMapping("/supported-types")
    public ResponseEntity<?> getSupportedTypes() {
        Map<String, Object> response = new HashMap<>();
        response.put("supportedTypes", new String[]{
            "application/pdf",
            "text/plain",
            "text/markdown",
            "text/html",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        });
        response.put("maxSize", "50MB");
        response.put("description", "支持 PDF、Word、Excel、PPT、TXT、Markdown、HTML 等常见文档格式");

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}
