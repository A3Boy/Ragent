# 技术文档：基于 DeepSeek 的 RAG 项目优化方案

## 1. 文档目的

本文档旨在指导开发团队（或自动化 AI 代理）完成对现有 RAG 应用的优化重构。核心目标包括：
- 解决当前系统中存在的 JPA 事务缺失与阿里云 DashScope API 认证失败问题；
- 将底层大语言模型从阿里云通义千问迁移至 **DeepSeek** 系列模型；
- 利用 DeepSeek 提供的先进特性（思考模式、工具调用、KV 缓存等）全面提升 RAG 的答案质量、交互体验与系统性能。

## 2. 现状与问题分析

通过分析 `2026-05-14` 的应用日志，发现以下两类阻断性问题：

| 问题类型 | 错误信息 | 影响范围 |
|---------|----------|----------|
| JPA 事务缺失 | `TransactionRequiredException: No EntityManager with actual transaction available for current thread - cannot reliably process 'remove' call` | 调用 `clearHistory` 接口删除用户对话历史时失败 |
| DashScope 401 认证 | `WebClientResponseException$Unauthorized: 401 Unauthorized from POST https://dashscope.aliyuncs.com/...` | 所有依赖 LLM 生成答案的流式/非流式 RAG 问答均失败 |

此外，现有实现仅使用基础对话生成能力，未充分发挥大模型在**推理透明性**、**工具调用**、**长文本成本优化**等方面的潜力。

## 3. DeepSeek 核心特性与 RAG 优化价值

DeepSeek API 提供五个关键能力，可针对性解决当前痛点并提升系统能力：

| 特性 | 能力描述 | 对 RAG 项目的优化价值 |
|------|----------|----------------------|
| **思考模式** | 模型先输出内部推理链 (`reasoning_content`)，再输出最终答案 (`content`) | 提升复杂问题（对比、解释、多步推理）的回答准确率；可向用户展示思维过程，增强可信度 |
| **多轮对话** | 通过传入完整对话历史实现上下文记忆 | 支持连续追问（如“第一个促销工具的适用场景是什么？”），提升交互自然度 |
| **前缀续写 (Beta)** | 基于已给的 `assistant` 前缀补全后续内容 | 加速文档续写、报告生成等场景，适合企业内部知识库的内容辅助创作 |
| **工具调用** | 模型主动调用外部函数（如查询数据库、调用 API） | 让 AI 能够执行业务动作（例如“创建促销活动”），从问答升级为智能体 |
| **KV 缓存** | 自动缓存重复的公共前缀（系统提示词、文档上下文） | **对 RAG 极为友好**：长文档首次处理后，后续问题复用缓存，延迟降低 50%~80%，成本减少 30%~60% |

## 4. 迁移实施方案

### 4.1 依赖调整

移除阿里云 DashScope 相关依赖，引入 DeepSeek 支持的 Spring AI 组件。

**推荐使用 Spring AI 官方 DeepSeek Starter（假设已存在），或手动配置 `OpenAiApi` 兼容客户端。**

#### Maven 依赖（方案一：Spring AI + DeepSeek 扩展）
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-deepseek-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version> <!-- 请查阅最新版本 -->
</dependency>
```

#### 若使用 Spring AI Alibaba（通过阿里云百炼调用 DeepSeek）
```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
    <version>2025.0.1</version>
</dependency>
```

#### 通用依赖
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 4.2 配置文件修改 (`application.yml`)

移除 `spring.ai.dashscope.*` 配置，添加 DeepSeek 配置。

```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}   # 从环境变量读取
      base-url: https://api.deepseek.com/v1
      chat:
        options:
          model: deepseek-chat          # 或 deepseek-reasoner（思考模式专用模型）
          temperature: 0.7
          max-tokens: 4096
# 如果使用 Spring AI Alibaba 方案，配置前缀为 spring.ai.alibaba.dashscope
```

### 4.3 代码改造要点

#### 4.3.1 事务问题修复（无论模型如何，都必须修复）

在 `RagService` 中添加 `@Transactional`：

```java
@Service
public class RagService {
    private final ChatHistoryRepository chatHistoryRepository;

    @Transactional
    public void clearHistory(String userId, String knowledgeBaseId) {
        chatHistoryRepository.deleteByUserIdAndKnowledgeBaseId(userId, knowledgeBaseId);
    }
}
```

#### 4.3.2 模型调用适配

原有 `ChatClient` 或 `ChatModel` 的创建方式保持不变（Spring AI 抽象统一），但需要调整为 DeepSeek 模型。

**示例：构建具备思考模式的聊天客户端**

```java
@Configuration
public class DeepSeekConfig {

    @Bean
    public ChatClient deepSeekChatClient(@Value("${spring.ai.deepseek.api-key}") String apiKey) {
        var openAiApi = new OpenAiApi("https://api.deepseek.com/v1", apiKey);
        var chatModel = new OpenAiChatModel(openAiApi,
                OpenAiChatOptions.builder()
                        .model("deepseek-reasoner")   // 思考模式专用模型
                        .temperature(0.7)
                        .build());
        return ChatClient.builder(chatModel).build();
    }
}
```

#### 4.3.3 启用思考模式与提取思维链

在调用时通过 `extraParams` 启用思考模式，并解析返回的 `reasoning_content`。

```java
public Mono<Flux<String>> streamRagAnswer(String question, String knowledgeBaseId) {
    List<Document> retrievedDocs = retrieveTopK(question, knowledgeBaseId);
    String prompt = buildRagPrompt(question, retrievedDocs);

    // 构建请求体时额外添加 thinking 参数
    var request = ChatRequest.builder()
            .messages(List.of(new UserMessage(prompt)))
            .options(OpenAiChatOptions.builder()
                    .withExtraParam("thinking", Map.of("type", "enabled"))
                    .withExtraParam("reasoning_effort", "medium")  // low/medium/high
                    .build())
            .build();

    return chatClient.stream(request)
            .map(chunk -> extractContent(chunk));  // 可同时输出 reasoning_content 与 content
}
```

#### 4.3.4 多轮对话历史管理

使用 Spring AI 的 `ChatMemory` 接口，推荐基于内存或 Redis 的实现。

```java
@Service
public class ConversationService {
    private final ChatMemory chatMemory;   // 注入具体实现

    public Flux<String> chat(String sessionId, String userMessage) {
        // 自动加载历史
        return chatClient.prompt()
                .user(userMessage)
                .advisors(advisor -> advisor.param("chat_memory_conversation_id", sessionId))
                .stream()
                .content();
    }
}
```

#### 4.3.5 工具调用示例（如查询促销活动）

定义 `@Tool` 方法，让模型自主决定调用。

```java
@Component
public class PromotionTools {

    @Tool(description = "根据促销工具名称和日期范围查询已创建的促销活动详情")
    public List<Promotion> queryPromotions(String toolName, String startDate, String endDate) {
        // 调用业务 Service 查询数据库
        return promotionService.findByToolAndDateRange(toolName, startDate, endDate);
    }
}

// 在服务中使用
@Autowired private PromotionTools promotionTools;

public Flux<String> agenticRag(String userInput) {
    return ChatClient.builder(chatModel)
            .defaultTools(promotionTools)   // 注册工具
            .build()
            .prompt()
            .user(userInput)
            .stream()
            .content();
}
```

#### 4.3.6 KV 缓存监控

无需代码修改，只需在日志中记录命中情况，便于观察优化效果。

```java
// 在拦截器或日志切面中打印
if (response.getMetadata().containsKey("usage")) {
    var usage = response.getMetadata().get("usage", Usage.class);
    log.info("KV cache hit tokens: {}, prompt tokens: {}", 
             usage.getPromptCacheHitTokens(), usage.getPromptTokens());
}
```

## 5. 针对性优化建议

### 5.1 复杂 RAG 问题开启思考模式

根据问题类型动态切换模型：

```java
public boolean isComplexQuestion(String question) {
    // 包含“对比”、“分析”、“为什么”、“如何影响”等关键词
    return question.matches(".*(对比|分析|原因|影响|区别|步骤).*");
}

// 在服务中：
String model = isComplexQuestion(question) ? "deepseek-reasoner" : "deepseek-chat";
```

### 5.2 利用 KV 缓存降低长文档成本

将固定的系统指令和知识库上下文作为公共前缀，确保每次请求中它们的位置**完全一致**（包括顺序和换行）。

```java
// 糟糕：每次都重新拼接，缓存难以命中
String prompt = "文档内容：" + docContent + "\n问题：" + question;

// 优化：固定顺序，让公共部分保持相同前缀
String prefix = "你是一个智能助手，基于以下文档回答问题。\n文档：\n" + docContent + "\n";
String suffix = "问题：" + question;
String finalPrompt = prefix + suffix;   // prefix 会触发缓存
```

### 5.3 工具调用实现“可操作型 RAG”

当用户询问“帮我创建一个秒杀促销活动”时，模型可以调用 `createPromotion` 工具，而不是仅仅给出文字建议。这要求你的业务系统暴露相应的 API。

## 6. 风险与注意事项

| 风险点 | 应对措施 |
|--------|----------|
| **思考模式 + 工具调用的上下文拼接** | 在多轮工具调用中，必须将上一轮的 `reasoning_content` 原样回传给 API。Spring AI 当前的自动历史管理可能无法直接支持，需要自定义 `ChatMemory` 实现或手动拼接消息。 |
| **Beta 功能稳定性** | 前缀续写、工具调用 `strict` 模式需使用 `base-url: https://api.deepseek.com/beta`，建议在非核心路径先试用。 |
| **限流差异** | DeepSeek 默认 RPM 限制可能低于阿里云，建议在上游增加令牌桶或 Resilience4j 限流。 |
| **成本变化** | DeepSeek 输入输出 token 定价不同，启用 KV 缓存后命中部分大幅优惠，但首次请求仍全价。建议设置预算告警。 |
| **模型能力边界** | deepseek-reasoner 在数学、逻辑推理上更强，但简单对话响应略慢。应根据场景路由。 |

## 7. 验收标准

完成迁移和优化后，应满足以下指标：

- [ ] `clearHistory` 接口删除操作正常执行，无事务异常日志。
- [ ] 所有 RAG 问答请求不再返回 401 认证错误。
- [ ] 启用思考模式时，API 返回包含 `reasoning_content` 字段，并可在前端展示。
- [ ] 连续多轮对话能正确记忆上下文（如“上面提到的第二个工具是什么？”能正确识别）。
- [ ] KV 缓存命中率统计日志正常输出（可通过 `prompt_cache_hit_tokens > 0` 验证）。
- [ ] 工具调用功能端到端通畅（例如“查询近一周的促销活动”能触发数据库查询并返回结果）。

## 8. 附录：关键代码示例汇总

### 8.1 带思考模式的流式 RAG 服务

```java
@Service
public class RagService {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public Flux<String> streamRagWithThinking(String question, String knowledgeBaseId, boolean enableThinking) {
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.query(question).withTopK(5));
        String prompt = buildPrompt(question, docs);
        
        var options = OpenAiChatOptions.builder()
                .model(enableThinking ? "deepseek-reasoner" : "deepseek-chat")
                .temperature(0.7);
        if (enableThinking) {
            options.withExtraParam("thinking", Map.of("type", "enabled"));
        }
        
        return chatClient.prompt(prompt)
                .options(options.build())
                .stream()
                .content();
    }
}
```

### 8.2 自定义 ChatMemory 支持思考模式（简化版）

```java
@Component
public class ReasoningAwareChatMemory implements ChatMemory {
    private final Map<String, List<Message>> store = new ConcurrentHashMap<>();

    @Override
    public void add(String conversationId, Message message) {
        store.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        var all = store.getOrDefault(conversationId, List.of());
        return all.stream().skip(Math.max(0, all.size() - lastN)).collect(Collectors.toList());
    }
    
    // 特别注意：当添加的 message 包含 reasoning_content 时，需保留该字段
    public void addWithReasoning(String conversationId, AssistantMessage message, String reasoningContent) {
        // 使用扩展字段存储 reasoning_content，或者自定义 Message 子类
    }
}
```

---

通过以上方案，你将构建一个**健壮、智能、低成本**的 RAG 系统，并为未来引入更多自主智能体能力打下坚实基础。请根据实际项目需求调整具体实现。