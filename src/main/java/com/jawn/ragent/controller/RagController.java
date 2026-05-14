package com.jawn.ragent.controller;

import com.jawn.ragent.dto.RagResponse;
import com.jawn.ragent.entity.Conversation;
import com.jawn.ragent.repository.ConversationRepository;
import com.jawn.ragent.repository.KnowledgeBaseRepository;
import com.jawn.ragent.service.RagService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rag")
@Slf4j
public class RagController {

    private final RagService ragService;
    private final KnowledgeBaseRepository kbRepository;
    private final ConversationRepository conversationRepository;

    public RagController(RagService ragService, KnowledgeBaseRepository kbRepository,
                         ConversationRepository conversationRepository) {
        this.ragService = ragService;
        this.kbRepository = kbRepository;
        this.conversationRepository = conversationRepository;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> ragChat(@RequestAttribute("userId") String userId,
                                     @RequestBody RagRequest request) {
        log.info("RAG 问答请求: {}", request.getQuestion());

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("问题不能为空"));
        }

        if (request.getKnowledgeBaseId() == null || request.getKnowledgeBaseId().isBlank()) {
            return ResponseEntity.badRequest().body(createErrorResponse("knowledgeBaseId 不能为空"));
        }

        // 校验知识库归属
        if (kbRepository.findByIdAndUserId(request.getKnowledgeBaseId(), userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int topK = request.getTopK() != null ? request.getTopK() : 5;
        int rounds = request.getConversationRounds() != null ? request.getConversationRounds() : 5;
        boolean enableThinking = Boolean.TRUE.equals(request.getEnableThinking());

        try {
            RagResponse ragResponse;
            if (request.getDocumentId() != null && !request.getDocumentId().isBlank()) {
                ragResponse = ragService.answerInDocument(
                        request.getQuestion(), request.getDocumentId(), topK,
                        request.getKnowledgeBaseId(), userId, rounds, enableThinking);
            } else {
                ragResponse = ragService.answer(
                        request.getQuestion(), topK,
                        request.getKnowledgeBaseId(), userId, rounds, enableThinking);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("answer", ragResponse.getAnswer());
            response.put("sources", ragResponse.getSources());
            response.put("question", request.getQuestion());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RAG 问答失败", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("问答失败: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ragStreamChat(@RequestAttribute("userId") String userId,
                                      @RequestBody RagRequest request) {
        log.info("RAG 流式问答请求: {}, cid: {}", request.getQuestion(), request.getConversationId());

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Flux.just("{\"type\":\"error\",\"message\":\"问题不能为空\"}");
        }

        if (request.getKnowledgeBaseId() == null || request.getKnowledgeBaseId().isBlank()) {
            return Flux.just("{\"type\":\"error\",\"message\":\"knowledgeBaseId 不能为空\"}");
        }

        if (kbRepository.findByIdAndUserId(request.getKnowledgeBaseId(), userId).isEmpty()) {
            return Flux.just("{\"type\":\"error\",\"message\":\"知识库不存在或无权访问\"}");
        }

        int topK = request.getTopK() != null ? request.getTopK() : 5;
        int rounds = request.getConversationRounds() != null ? request.getConversationRounds() : 5;
        boolean enableThinking = Boolean.TRUE.equals(request.getEnableThinking());

        return ragService.streamAnswer(request.getQuestion(), topK,
                        request.getKnowledgeBaseId(), userId, rounds, enableThinking,
                        request.getConversationId(), request.getProvider())
                .onErrorResume(e -> {
                    log.error("RAG 流式问答失败", e);
                    return Flux.just("{\"type\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
                });
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchDocuments(@RequestAttribute("userId") String userId,
                                             @RequestBody SearchRequest request) {
        log.info("文档搜索请求: {}", request.getQuery());

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("查询内容不能为空"));
        }

        if (request.getKnowledgeBaseId() == null || request.getKnowledgeBaseId().isBlank()) {
            return ResponseEntity.badRequest().body(createErrorResponse("knowledgeBaseId 不能为空"));
        }

        if (kbRepository.findByIdAndUserId(request.getKnowledgeBaseId(), userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int topK = request.getTopK() != null ? request.getTopK() : 5;

        try {
            List<Document> docs = ragService.getRelevantDocuments(request.getQuery(), request.getKnowledgeBaseId(), topK);

            List<Map<String, Object>> results = docs.stream().map(doc -> {
                Map<String, Object> item = new HashMap<>();
                item.put("text", doc.getText());
                item.put("metadata", doc.getMetadata());
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("query", request.getQuery());
            response.put("results", results);
            response.put("total", results.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("文档搜索失败", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("搜索失败: " + e.getMessage()));
        }
    }

    @GetMapping("/history/{kbId}")
    public ResponseEntity<?> getHistory(@RequestAttribute("userId") String userId,
                                        @PathVariable String kbId,
                                        @RequestParam(defaultValue = "50") int limit) {
        List<Conversation> conversations = conversationRepository
                .findByUserIdAndKnowledgeBaseIdOrderByCreatedAtDesc(userId, kbId, PageRequest.of(0, limit));

        List<Map<String, Object>> items = conversations.stream().map(c -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("userId", c.getUserId());
            item.put("knowledgeBaseId", c.getKnowledgeBaseId());
            item.put("question", c.getQuestion());
            item.put("answer", c.getAnswer());
            item.put("createdAt", c.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    /**
     * 轻量对话列表 — 仅返回侧边栏需要的信息
     */
    @GetMapping("/conversations/{kbId}")
    public ResponseEntity<?> getConversations(@RequestAttribute("userId") String userId,
                                               @PathVariable String kbId,
                                               @RequestParam(defaultValue = "50") int limit) {
        List<Conversation> conversations = conversationRepository
                .findByUserIdAndKnowledgeBaseIdOrderByCreatedAtDesc(userId, kbId, PageRequest.of(0, limit));

        List<Map<String, Object>> items = conversations.stream().map(c -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("title", c.getQuestion().length() > 50
                    ? c.getQuestion().substring(0, 50) + "..." : c.getQuestion());
            item.put("createdAt", c.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    /**
     * 单个对话完整消息
     */
    @GetMapping("/conversations/{kbId}/{convId}")
    public ResponseEntity<?> getConversation(@RequestAttribute("userId") String userId,
                                              @PathVariable String kbId,
                                              @PathVariable String convId) {
        Conversation conv = conversationRepository.findById(convId).orElse(null);

        if (conv == null || !conv.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", conv.getId());
        item.put("userId", conv.getUserId());
        item.put("knowledgeBaseId", conv.getKnowledgeBaseId());
        item.put("question", conv.getQuestion());
        item.put("answer", conv.getAnswer());
        item.put("createdAt", conv.getCreatedAt());

        return ResponseEntity.ok(item);
    }

    @Transactional
    @DeleteMapping("/history/{kbId}")
    public ResponseEntity<?> clearHistory(@RequestAttribute("userId") String userId,
                                           @PathVariable String kbId) {
        conversationRepository.deleteByUserIdAndKnowledgeBaseId(userId, kbId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "对话历史已清空");
        return ResponseEntity.ok(response);
    }

    @Transactional
    @DeleteMapping("/conversations/{kbId}/{convId}")
    public ResponseEntity<?> deleteConversation(@RequestAttribute("userId") String userId,
                                                 @PathVariable String kbId,
                                                 @PathVariable String convId) {
        conversationRepository.deleteByIdAndUserId(convId, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "对话已删除");
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }

    @Data
    public static class RagRequest {
        private String question;
        private Integer topK;
        private String documentId;
        private String knowledgeBaseId;
        private Integer conversationRounds;
        private Boolean enableThinking;
        private String conversationId;
        private String provider;
    }

    @Data
    public static class SearchRequest {
        private String query;
        private Integer topK;
        private String knowledgeBaseId;
    }
}
