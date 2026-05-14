package com.jawn.ragent.service;

import com.jawn.ragent.config.ChatClientRouter;
import com.jawn.ragent.config.ProviderProperties;
import com.jawn.ragent.dto.RagResponse;
import com.jawn.ragent.entity.Conversation;
import com.jawn.ragent.repository.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class RagService {

    private final DocumentService documentService;
    private final ConversationRepository conversationRepository;
    private final ChatClient chatClient;
    private final ChatClientRouter chatClientRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String RAG_SYSTEM_PROMPT = """
    你是一个基于知识库的智能问答助手。请根据提供的参考文档内容回答用户问题。

    【核心原则】
    - 只能基于提供的参考文档回答
    - 不允许使用外部知识或主观推测

    【回答规则】
    1. 优先使用参考文档中的信息回答问题
    2. 回答必须基于具体片段内容，不允许泛化或主观总结
    3. 不要编造信息，无法确定的内容不要输出
    4. 回答要简洁明了，突出重点

    【引用规则】
    5. 回答时必须标注引用片段编号，格式为 [片段N]
    6. ⚠️ 引用编号必须对应提供的片段，不允许编造不存在的编号
    7. 每一个结论必须能在引用片段中找到依据
    8. 不允许出现"无引用支撑"的结论

    【URL 规则（非常重要）】
    9. 如果参考文档中包含 URL：
       - 只能使用文档中已有的 URL
       - 不允许编造、修改或补全 URL
       - 不允许对 URL 做"官方/权威"等评价性描述

    【禁止行为】
    10. 禁止输出以下内容：
       - "根据经验""通常来说""一般认为"等外部推断
       - 对来源的主观评价（如"官方""权威""推荐"）
       - 未在文档中出现的信息

    【数学公式规则】
    11. 当回答涉及数学公式时：
       - 行内公式使用 $...$ 包裹
       - 块级公式使用 $$...$$ 包裹
       - 使用标准 LaTeX 语法
       - 禁止输出裸露的 \\frac、\\lim、\\sum 等 LaTeX 命令

    【无法回答】
    12. 如果参考文档中没有相关信息：
       - 直接回答："未在知识库中找到明确依据，建议换个问法或补充文档。"
       - 不要进行任何推测或扩展

    【参考文档】
    {context}
    """;

    public RagService(DocumentService documentService, ConversationRepository conversationRepository,
                       ChatClient chatClient, ChatClientRouter chatClientRouter) {
        this.documentService = documentService;
        this.conversationRepository = conversationRepository;
        this.chatClient = chatClient;
        this.chatClientRouter = chatClientRouter;
    }

    /**
     * RAG 问答 - 基于指定知识库（带引用来源 + 对话历史）
     */
    public RagResponse answer(String question, int topK, String knowledgeBaseId, String userId,
                              int conversationRounds, boolean enableThinking) {
        log.info("RAG 问答请求: {}, topK: {}, 知识库: {}, thinking: {}", question, topK, knowledgeBaseId, enableThinking);

        List<Document> relevantDocs = searchAndFilterDocuments(question, knowledgeBaseId, topK);

        if (relevantDocs.isEmpty()) {
            log.warn("未找到相关文档片段");
            RagResponse response = new RagResponse();
            response.setAnswer("知识库中暂无相关文档，请先上传文档后再提问。");
            response.setSources(List.of());
            return response;
        }

        String context = buildContext(relevantDocs);
        List<Message> messages = buildMessagesWithHistory(userId, knowledgeBaseId, question, conversationRounds, context);

        OpenAiChatOptions options = buildThinkingOptions(enableThinking);
        String answer = chatClient.prompt(new Prompt(messages, options))
                .call()
                .content();

        List<RagResponse.Source> sources = buildSources(relevantDocs);

        ValidationResult validationResult = validateAndCleanReferences(answer, sources);
        answer = validationResult.cleanedAnswer;
        Set<Integer> finalValidIndexes = validationResult.validIndexes;

        sources = sources.stream()
                .filter(s -> finalValidIndexes.contains(s.getSnippetIndex()))
                .collect(Collectors.toList());

        if (sources.isEmpty()) {
            log.warn("答案中未找到有效引用，拒绝返回空回答");
            RagResponse response = new RagResponse();
            response.setAnswer("未在知识库中找到明确依据，建议换个问法或补充文档。");
            response.setSources(List.of());
            return response;
        }

        // 保存对话历史
        saveConversation(userId, knowledgeBaseId, question, answer);

        RagResponse response = new RagResponse();
        response.setAnswer(answer);
        response.setSources(sources);

        log.info("RAG 问答完成，返回 {} 个引用来源", sources.size());
        return response;
    }

    /**
     * RAG 问答 - 基于指定知识库的特定文档
     */
    public RagResponse answerInDocument(String question, String documentId, int topK,
                                         String knowledgeBaseId, String userId, int conversationRounds,
                                         boolean enableThinking) {
        log.info("RAG 问答请求(指定文档): {}, documentId: {}, 知识库: {}, thinking: {}",
                question, documentId, knowledgeBaseId, enableThinking);

        List<Document> relevantDocs = searchAndFilterDocumentsInDocument(question, documentId, knowledgeBaseId, topK);

        if (relevantDocs.isEmpty()) {
            RagResponse response = new RagResponse();
            response.setAnswer("该文档中没有找到与问题相关的内容。");
            response.setSources(List.of());
            return response;
        }

        String context = buildContext(relevantDocs);
        List<Message> messages = buildMessagesWithHistory(userId, knowledgeBaseId, question, conversationRounds, context);

        OpenAiChatOptions options = buildThinkingOptions(enableThinking);
        String answer = chatClient.prompt(new Prompt(messages, options))
                .call()
                .content();

        List<RagResponse.Source> sources = buildSources(relevantDocs);

        ValidationResult validationResult = validateAndCleanReferences(answer, sources);
        answer = validationResult.cleanedAnswer;
        Set<Integer> finalValidIndexes = validationResult.validIndexes;

        sources = sources.stream()
                .filter(s -> finalValidIndexes.contains(s.getSnippetIndex()))
                .collect(Collectors.toList());

        if (sources.isEmpty()) {
            RagResponse response = new RagResponse();
            response.setAnswer("该文档中未找到明确依据，建议换个问法。");
            response.setSources(List.of());
            return response;
        }

        saveConversation(userId, knowledgeBaseId, question, answer);

        RagResponse response = new RagResponse();
        response.setAnswer(answer);
        response.setSources(sources);

        log.info("RAG 问答完成（指定文档），返回 {} 个引用来源", sources.size());
        return response;
    }

    /**
     * 流式 RAG 问答 — SSE JSON 协议，流完成后保存对话历史
     *
     * 当 enableThinking=true 时，使用 raw HTTP 调用 DeepSeek API 以捕获 reasoning_content。
     * 否则使用 Spring AI ChatClient 标准流式调用。
     */
    public reactor.core.publisher.Flux<String> streamAnswer(String question, int topK,
                                                             String knowledgeBaseId, String userId,
                                                             int conversationRounds, boolean enableThinking,
                                                             String conversationId, String provider) {
        log.info("流式 RAG 问答请求: {}, topK: {}, 知识库: {}, thinking: {}, provider: {}, cid: {}",
                question, topK, knowledgeBaseId, enableThinking, provider, conversationId);

        List<Document> relevantDocs = searchAndFilterDocuments(question, knowledgeBaseId, topK);

        if (relevantDocs.isEmpty()) {
            return reactor.core.publisher.Flux.just(
                    sseError("知识库中暂无相关文档，请先上传文档后再提问。"));
        }

        String context = buildContext(relevantDocs);
        List<Message> messages = buildMessagesWithHistory(userId, knowledgeBaseId, question, conversationRounds, context);

        final String cid = (conversationId != null && !conversationId.isBlank())
                ? conversationId : java.util.UUID.randomUUID().toString();

        if (enableThinking) {
            return streamWithThinking(messages, cid, userId, knowledgeBaseId, question, provider);
        }

        // 标准流式（无 thinking）
        StringBuilder answerBuilder = new StringBuilder();
        OpenAiChatOptions options = buildThinkingOptions(false);
        return chatClient.prompt(new Prompt(messages, options))
                .stream()
                .content()
                .map(chunk -> {
                    answerBuilder.append(chunk);
                    return sseChunk(chunk);
                })
                .doOnComplete(() -> {
                    String fullAnswer = answerBuilder.toString();
                    if (!fullAnswer.isEmpty()) {
                        saveConversationWithId(cid, userId, knowledgeBaseId, question, fullAnswer);
                        log.info("流式对话已保存: cid={}, 答案长度={}", cid, fullAnswer.length());
                    }
                })
                .concatWith(reactor.core.publisher.Flux.just(sseDone(cid)))
                .onErrorResume(e -> {
                    log.error("流式问答失败", e);
                    return reactor.core.publisher.Flux.just(sseError(e.getMessage()));
                });
    }

    /**
     * 使用 raw HTTP 调用 DeepSeek API，捕获 reasoning_content 并发送 thinking SSE 事件。
     */
    private reactor.core.publisher.Flux<String> streamWithThinking(
            List<Message> messages, String cid, String userId,
            String knowledgeBaseId, String question, String provider) {

        ProviderProperties.ProviderConfig config = chatClientRouter.getProviderConfig(provider);
        if (config == null) {
            return reactor.core.publisher.Flux.just(
                    sseError("未找到 provider 配置: " + (provider != null ? provider : "默认")));
        }

        String url = config.getBaseUrl() + "/chat/completions";
        String apiKey = config.getApiKey();
        String model = config.getModel();

        // 构建 DeepSeek 兼容的请求体
        List<Map<String, String>> apiMessages = messages.stream().map(msg -> {
            Map<String, String> m = new LinkedHashMap<>();
            if (msg instanceof SystemMessage) {
                m.put("role", "system");
                m.put("content", msg.getText());
            } else if (msg instanceof UserMessage) {
                m.put("role", "user");
                m.put("content", msg.getText());
            } else if (msg instanceof AssistantMessage) {
                m.put("role", "assistant");
                m.put("content", msg.getText());
            }
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", apiMessages);
        body.put("stream", true);
        body.put("extra_body", Map.of("thinking", Map.of("type", "enabled")));

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return reactor.core.publisher.Flux.just(sseError("构建请求失败: " + e.getMessage()));
        }

        log.info("Thinking 模式: raw HTTP 调用 {}, model: {}", url, model);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMinutes(5))
                .build();

        return reactor.core.publisher.Mono.fromCallable(() -> {
            HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                log.error("DeepSeek API 返回错误: status={}, body={}", response.statusCode(), errorBody);
                return reactor.core.publisher.Flux.just(sseError("API 返回错误: " + response.statusCode()));
            }

            StringBuilder answerBuilder = new StringBuilder();
            reactor.core.publisher.FluxSink<String>[] sinkRef = new reactor.core.publisher.FluxSink[1];

            reactor.core.publisher.Flux<String> flux = reactor.core.publisher.Flux.create(sink -> {
                sinkRef[0] = sink;

                response.body().forEachOrdered(line -> {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("data:")) return;

                    String json = trimmed.substring(5).trim();
                    if ("[DONE]".equals(json)) return;

                    try {
                        var node = objectMapper.readTree(json);
                        var choices = node.get("choices");
                        if (choices == null || !choices.isArray() || choices.isEmpty()) return;

                        var choice = choices.get(0);
                        var delta = choice.get("delta");
                        if (delta == null) return;

                        // DeepSeek thinking: reasoning_content 在 delta 中
                        var reasoningNode = delta.get("reasoning_content");
                        if (reasoningNode != null && !reasoningNode.isNull()) {
                            String thinking = reasoningNode.asText();
                            if (!thinking.isEmpty()) {
                                sink.next(sseThinking(thinking));
                            }
                        }

                        // 正常 content
                        var contentNode = delta.get("content");
                        if (contentNode != null && !contentNode.isNull()) {
                            String content = contentNode.asText();
                            if (!content.isEmpty()) {
                                answerBuilder.append(content);
                                sink.next(sseChunk(content));
                            }
                        }

                        // 检查 finish_reason
                        var finishReason = choice.get("finish_reason");
                        if (finishReason != null && "stop".equals(finishReason.asText())) {
                            // 结束
                        }
                    } catch (Exception e) {
                        log.warn("解析 SSE 行失败: {}", e.getMessage());
                    }
                });

                // 完成
                String fullAnswer = answerBuilder.toString();
                if (!fullAnswer.isEmpty()) {
                    saveConversationWithId(cid, userId, knowledgeBaseId, question, fullAnswer);
                    log.info("Thinking 流式对话已保存: cid={}, 答案长度={}", cid, fullAnswer.length());
                }
                sink.next(sseDone(cid));
                sink.complete();
            });

            return flux;
        }).flatMapMany(f -> f);
    }

    /**
     * RAG 问答（带完整 sources 返回）
     */
    public RagResponse streamAnswerWithSources(String question, int topK,
                                                String knowledgeBaseId, String userId,
                                                int conversationRounds, boolean enableThinking) {
        log.info("RAG 问答（带源）请求: {}, topK: {}, 知识库: {}, thinking: {}", question, topK, knowledgeBaseId, enableThinking);

        List<Document> relevantDocs = searchAndFilterDocuments(question, knowledgeBaseId, topK);

        if (relevantDocs.isEmpty()) {
            RagResponse response = new RagResponse();
            response.setAnswer("知识库中暂无相关文档，请先上传文档后再提问。");
            response.setSources(List.of());
            return response;
        }

        String context = buildContext(relevantDocs);
        List<Message> messages = buildMessagesWithHistory(userId, knowledgeBaseId, question, conversationRounds, context);

        OpenAiChatOptions options = buildThinkingOptions(enableThinking);
        String answer = chatClient.prompt(new Prompt(messages, options))
                .call()
                .content();

        List<RagResponse.Source> sources = buildSources(relevantDocs);

        ValidationResult validationResult = validateAndCleanReferences(answer, sources);
        answer = validationResult.cleanedAnswer;
        Set<Integer> finalValidIndexes = validationResult.validIndexes;

        sources = sources.stream()
                .filter(s -> finalValidIndexes.contains(s.getSnippetIndex()))
                .collect(Collectors.toList());

        if (sources.isEmpty()) {
            RagResponse response = new RagResponse();
            response.setAnswer("未在知识库中找到明确依据，建议换个问法或补充文档。");
            response.setSources(List.of());
            return response;
        }

        saveConversation(userId, knowledgeBaseId, question, answer);

        RagResponse response = new RagResponse();
        response.setAnswer(answer);
        response.setSources(sources);

        log.info("RAG 问答完成，返回 {} 个引用来源", sources.size());
        return response;
    }

    /**
     * 获取检索到的相关文档
     */
    public List<Document> getRelevantDocuments(String question, String knowledgeBaseId, int topK) {
        return searchAndFilterDocuments(question, knowledgeBaseId, topK);
    }

    // ========== 内部方法 ==========

    private OpenAiChatOptions buildThinkingOptions(boolean enableThinking) {
        if (enableThinking) {
            log.info("已启用 DeepSeek thinking 模式");
            return OpenAiChatOptions.builder()
                    .extraBody(Map.of("thinking", Map.of("type", "enabled")))
                    .build();
        }
        return OpenAiChatOptions.builder().build();
    }

    private String buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append("[片段 ").append(i + 1).append("]\n");
            context.append("来源: ").append(doc.getMetadata().getOrDefault("document_name", "未知")).append("\n");
            context.append(doc.getText()).append("\n\n");
        }
        return context.toString();
    }

    private List<RagResponse.Source> buildSources(List<Document> documents) {
        return IntStream.range(0, documents.size())
                .mapToObj(idx -> {
                    RagResponse.Source source = new RagResponse.Source();
                    source.setSnippetIndex(idx + 1);
                    Document doc = documents.get(idx);
                    source.setFileName((String) doc.getMetadata().getOrDefault("document_name", "未知"));
                    source.setContent(truncate(doc.getText(), 300));
                    source.setUrls(extractUrls(doc.getText()));
                    return source;
                })
                .collect(Collectors.toList());
    }

    private boolean isUseful(Document doc) {
        String text = doc.getText();
        if (text == null || text.isBlank()) return false;

        String trimmed = text.trim();

        if (trimmed.length() < 50) return false;

        String[] lines = trimmed.split("\n");
        long urlLines = 0;
        long meaningfulLines = 0;
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            if (l.contains("http")) urlLines++;
            else meaningfulLines++;
        }
        if (meaningfulLines == 0) return false;
        if (urlLines > meaningfulLines) return false;

        return true;
    }

    private List<Document> searchAndFilterDocuments(String question, String knowledgeBaseId, int topK) {
        int fetchSize = Math.max(topK * 3, 15);
        List<Document> docs = documentService.searchInKnowledgeBase(question, knowledgeBaseId, fetchSize);

        int originalSize = docs.size();

        List<Document> filtered = docs.stream()
                .filter(this::isUseful)
                .collect(Collectors.toList());

        int filteredSize = filtered.size();

        List<Document> finalDocs = filtered.stream()
                .limit(topK)
                .collect(Collectors.toList());

        log.info("检索管道: 原始 {} → 过滤后 {} → 最终用 {}",
                originalSize, filteredSize, finalDocs.size());

        if (filteredSize < originalSize) {
            log.info("过滤掉了 {} 个低质量片段", originalSize - filteredSize);
        }

        return finalDocs;
    }

    private List<Document> searchAndFilterDocumentsInDocument(String question, String documentId,
                                                               String knowledgeBaseId, int topK) {
        int fetchSize = Math.max(topK * 3, 15);
        List<Document> docs = documentService.searchInDocument(question, documentId, knowledgeBaseId, fetchSize);

        int originalSize = docs.size();

        List<Document> filtered = docs.stream()
                .filter(this::isUseful)
                .collect(Collectors.toList());

        int filteredSize = filtered.size();

        List<Document> finalDocs = filtered.stream()
                .limit(topK)
                .collect(Collectors.toList());

        log.info("检索管道(指定文档): 原始 {} → 过滤后 {} → 最终用 {}",
                originalSize, filteredSize, finalDocs.size());

        return finalDocs;
    }

    /**
     * 构建包含对话历史的完整 messages 列表
     * 顺序：SystemMessage → [历史 User/Assistant 交替] → 当前 UserMessage
     */
    private List<Message> buildMessagesWithHistory(String userId, String knowledgeBaseId,
                                                    String currentQuestion, int rounds, String context) {
        List<Message> messages = new ArrayList<>();

        // 1. SystemMessage（RAG 系统提示 + 检索到的文档上下文）
        String systemText = RAG_SYSTEM_PROMPT.replace("{context}", context);
        messages.add(new SystemMessage(systemText));

        // 2. 历史对话（从数据库加载，按时间正序排列）
        if (rounds > 0) {
            List<Conversation> history = conversationRepository
                    .findByUserIdAndKnowledgeBaseIdOrderByCreatedAtDesc(
                            userId, knowledgeBaseId, PageRequest.of(0, rounds));

            if (!history.isEmpty()) {
                Collections.reverse(history); // 最旧的在前
                for (Conversation c : history) {
                    messages.add(new UserMessage(c.getQuestion()));
                    messages.add(new AssistantMessage(c.getAnswer()));
                }
                log.info("加载对话历史 {} 轮，共 {} 条消息", history.size(), messages.size() - 1);
            }
        }

        // 3. 当前用户提问
        messages.add(new UserMessage(currentQuestion));

        return messages;
    }

    private void saveConversation(String userId, String knowledgeBaseId, String question, String answer) {
        saveConversationWithId(null, userId, knowledgeBaseId, question, answer);
    }

    private void saveConversationWithId(String conversationId, String userId, String knowledgeBaseId,
                                         String question, String answer) {
        try {
            var builder = Conversation.builder()
                    .userId(userId)
                    .knowledgeBaseId(knowledgeBaseId)
                    .question(question)
                    .answer(answer);
            if (conversationId != null && !conversationId.isBlank()) {
                builder.id(conversationId);
            }
            conversationRepository.save(builder.build());
        } catch (Exception e) {
            log.error("保存对话历史失败", e);
        }
    }

    // ========== SSE JSON 协议帮助方法 ==========

    private String sseThinking(String content) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("type", "thinking", "content", content));
        } catch (Exception e) {
            return "{\"type\":\"thinking\",\"content\":\"\"}";
        }
    }

    private String sseChunk(String content) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("type", "chunk", "content", content));
        } catch (Exception e) {
            return "{\"type\":\"chunk\",\"content\":\"\"}";
        }
    }

    private String sseDone(String conversationId) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("type", "done", "conversationId", conversationId));
        } catch (Exception e) {
            return "{\"type\":\"done\",\"conversationId\":\"\"}";
        }
    }

    private String sseError(String message) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("type", "error", "message", message));
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"unknown\"}";
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private ValidationResult validateAndCleanReferences(String answer, List<RagResponse.Source> sources) {
        if (answer == null || answer.isEmpty()) {
            return new ValidationResult(answer, Set.of());
        }

        Pattern pattern = Pattern.compile("\\[片段\\s*(\\d+)\\]");
        Matcher matcher = pattern.matcher(answer);
        Set<Integer> usedIndexes = new HashSet<>();
        while (matcher.find()) {
            usedIndexes.add(Integer.parseInt(matcher.group(1)));
        }

        Set<Integer> validIndexes = sources.stream()
                .map(RagResponse.Source::getSnippetIndex)
                .collect(Collectors.toSet());

        Set<Integer> invalidIndexes = new HashSet<>(usedIndexes);
        invalidIndexes.removeAll(validIndexes);

        String cleanedAnswer = answer;
        if (!invalidIndexes.isEmpty()) {
            log.warn("检测到 {} 个非法引用编号: {}", invalidIndexes.size(), invalidIndexes);
            for (Integer invalidIdx : invalidIndexes) {
                cleanedAnswer = cleanedAnswer.replaceAll("\\[片段\\s*" + invalidIdx + "\\]", "");
            }
            log.info("已清理非法引用，答案长度: {} → {}", answer.length(), cleanedAnswer.length());
        }

        Set<Integer> finalValidIndexes = new HashSet<>(usedIndexes);
        finalValidIndexes.removeAll(invalidIndexes);

        return new ValidationResult(cleanedAnswer, finalValidIndexes);
    }

    private List<String> extractUrls(String text) {
        if (text == null || text.isEmpty()) return List.of();

        List<String> urls = new ArrayList<>();
        Pattern urlPattern = Pattern.compile("https?://[^\\s<>\"）\\)\\]】]+");
        Matcher matcher = urlPattern.matcher(text);
        while (matcher.find()) {
            String url = matcher.group();
            while (url.endsWith(".") || url.endsWith(",") || url.endsWith("。") || url.endsWith("、")) {
                url = url.substring(0, url.length() - 1);
            }
            if (!urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private static class ValidationResult {
        String cleanedAnswer;
        Set<Integer> validIndexes;

        ValidationResult(String cleanedAnswer, Set<Integer> validIndexes) {
            this.cleanedAnswer = cleanedAnswer;
            this.validIndexes = validIndexes;
        }
    }
}
