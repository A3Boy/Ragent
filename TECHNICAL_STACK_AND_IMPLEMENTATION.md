# RAGent 技术栈与功能实现详解

> **项目名称**: RAGent - 企业级 RAG 多智能体知识库  
> **版本**: v0.0.1-SNAPSHOT  
> **文档日期**: 2026年5月13日  
> **维护者**: Jawn Team  

---

## 一、项目概述

### 1.1 项目定位

RAGent 是一个基于 **检索增强生成 (Retrieval-Augmented Generation, RAG)** 的企业级智能知识库问答系统。系统的核心设计理念是：

> **RAG 的本质不是让模型更聪明，而是让结果更可控、更可信。**

与传统的直接对话式 AI 不同，RAGent 通过以下机制确保回答的准确性和可追溯性：

- **向量检索**：从知识库中精准定位相关文档片段
- **质量控制管道**：多层过滤机制，确保上下文质量
- **引用校验**：自动检测并清理 AI 编造的伪引用
- **空引用保护**：无证据不回答，杜绝幻觉和编造
- **URL 提取**：自动从文档中提取链接，方便用户溯源

### 1.2 应用场景

- 📚 企业内部知识库问答（员工手册、政策文档、技术规范）
- 🎓 教育培训资料检索
- 📋 客服知识库自动问答
- 🔬 科研文献快速检索
- 📖 产品文档智能搜索

---

## 二、完整技术栈

### 2.1 后端技术栈

| 技术 | 版本 | 用途 | 说明 |
|------|------|------|------|
| **Java** | 17 | 编程语言 | LTS 版本，支持 Records、Sealed Classes 等现代特性 |
| **Spring Boot** | 3.3.5 | 应用框架 | 自动配置、嵌入式 Web 服务器、生产就绪特性 |
| **Spring AI** | 1.1.0 | AI 集成框架 | 统一的 AI 抽象层，支持多种模型和向量存储 |
| **Spring AI Alibaba** | 1.1.2.0 | 阿里云 AI 集成 | DashScope API 封装、自动配置 |
| **Spring Data JPA** | 3.3.5 (继承) | 数据访问 | ORM 框架，自动建表 |
| **PostgreSQL** | 16 (Docker) | 关系型数据库 | 持久化存储、向量存储宿主 |
| **PgVector** | pg16 | 向量数据库 | HNSW 索引、COSINE 距离度量 |
| **Apache Tika** | (Spring AI 封装) | 文档解析 | 支持 PDF、Word、Excel、PPT、TXT、MD、HTML 等 |
| **Lombok** | (继承) | 代码简化 | @Data、@Slf4j 等注解 |
| **Maven** | 3.x | 构建工具 | 依赖管理、打包 |

### 2.2 大模型平台

| 平台 | 模型 | 用途 | 接入方式 |
|------|------|------|----------|
| **阿里云百炼** | qwen-plus | Chat 对话 | OpenAI 兼容模式 (DashScope) |
| **阿里云百炼** | tongyi-embedding-vision-flash | Embedding 向量化 | Spring AI Alibaba 自动配置 |
| **阿里云百炼** | qwen3-vl-rerank | Rerank 重排序 | OpenAI 兼容模式 |
| **DeepSeek** (可选) | deepseek-chat | Chat 对话 | OpenAI 兼容模式 |
| **Ollama** (可选) | qwen2.5:7b | 本地模型 | OpenAI 兼容模式 |

### 2.3 前端技术栈

| 技术 | 用途 |
|------|------|
| **HTML5** | 页面结构 |
| **CSS3** | 样式（渐变、动画、响应式） |
| **JavaScript (Vanilla)** | 交互逻辑 |
| **Fetch API** | HTTP 请求 |
| **EventSource / ReadableStream** | SSE 流式接收 |

### 2.4 基础设施

| 工具 | 用途 |
|------|------|
| **Docker Compose** | PgVector 容器化部署 |
| **Spring Boot DevTools** | 开发热重载 |
| **Spring Boot Maven Plugin** | 打包可执行 JAR |

---

## 三、核心架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端层 (Browser)                         │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    rag-test.html                         │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐  │    │
│  │  │ 文档上传  │  │ RAG 问答  │  │ 流式问答 + 引用来源   │  │    │
│  │  └────┬─────┘  └────┬─────┘  └──────────┬───────────┘  │    │
│  └───────┼─────────────┼───────────────────┼──────────────┘    │
└──────────┼─────────────┼───────────────────┼───────────────────┘
           │             │                   │
           │  HTTP REST  │  SSE Stream       │
           ▼             ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Controller 层 (Spring MVC)                   │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │ DocumentController│  │  RagController   │  │ ChatController │  │
│  │                  │  │                  │  │                │  │
│  │ POST /upload     │  │ POST /rag/chat   │  │ POST /chat     │  │
│  │ DELETE /{id}     │  │ POST /rag/stream │  │ POST /stream   │  │
│  │ GET /types       │  │ POST /rag/search │  │ GET /providers │  │
│  └────────┬─────────┘  └────────┬─────────┘  └───────┬────────┘  │
└───────────┼─────────────────────┼────────────────────┼──────────┘
            │                     │                    │
            ▼                     ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Service 层 (业务逻辑)                       │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   DocumentService                        │    │
│  │  • 文档解析 (Tika)                                       │    │
│  │  • 文本分块 (TokenTextSplitter: 500 tokens, 100 overlap) │    │
│  │  • 元数据注入 (document_id, chunk_index, upload_time)     │    │
│  │  • 批量向量化 (batch size=10)                             │    │
│  │  • 向量存储 (PgVector)                                   │    │
│  │  • 相似度检索 (SearchRequest + filterExpression)          │    │
│  └───────────────────────────┬─────────────────────────────┘    │
│                              │                                   │
│  ┌───────────────────────────┴─────────────────────────────┐    │
│  │                      RagService                          │    │
│  │  • 检索增强生成 (RAG) 核心流程                           │    │
│  │  • 质量控制管道: 多取(3x) → 过滤垃圾 → 裁剪 topK         │    │
│  │  • 上下文构建: [片段N] 格式标注                          │    │
│  │  • 引用合法性校验: 正则检测伪引用                        │    │
│  │  • 空引用保护: 无证据不回答                              │    │
│  │  • URL 自动提取: 从 chunk 中提取 http/https 链接         │    │
│  │  • 流式输出: Reactor Flux<String>                        │    │
│  └───────────────────────────┬─────────────────────────────┘    │
└──────────────────────────────┼──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Config 层 (配置与路由)                      │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ ChatClientRouter  │  │ProviderProperties│                    │
│  │                  │  │                  │                    │
│  │ • 多模型动态路由  │  │ • YAML 配置绑定  │                    │
│  │ • DashScopeApi   │  │ • Provider 列表  │                    │
│  │ • DashScopeChat  │  │ • 默认模型选择   │                    │
│  │ • ChatClient Map │  │ • 启用/禁用控制  │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │    RagConfig      │  │ VectorStoreConfig│                    │
│  │                  │  │                  │                    │
│  │ • RAG ChatClient │  │ • Embedding 自动 │                    │
│  │ • 默认 Provider  │  │   装配 (DashScope)│                    │
│  └──────────────────┘  └──────────────────┘                    │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      数据与 AI 层                                │
│                                                                 │
│  ┌──────────────────────┐    ┌──────────────────────────────┐   │
│  │   PostgreSQL + PgVector│    │   阿里云百炼 (DashScope)      │   │
│  │                       │    │                              │   │
│  │ • 向量存储表          │    │ • Chat Model: qwen-plus      │   │
│  │ • HNSW 索引           │    │ • Embedding: text-embedding  │   │
│  │ • COSINE_DISTANCE     │    │ • Rerank: qwen3-vl-rerank    │   │
│  │ • 1024 维度           │    │ • OpenAI 兼容 API            │   │
│  │ • metadata 过滤       │    │                              │   │
│  └──────────────────────┘    └──────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 分层架构说明

| 层级 | 职责 | 核心类 |
|------|------|--------|
| **表现层** | HTTP 接口、请求验证、响应格式化 | `ChatController`, `DocumentController`, `RagController` |
| **业务层** | 核心业务逻辑、RAG 流程、质量控制 | `DocumentService`, `RagService` |
| **配置层** | Bean 配置、模型路由、属性绑定 | `ChatClientRouter`, `ProviderProperties`, `RagConfig`, `VectorStoreConfig` |
| **数据层** | 向量存储、文档解析、Embedding | PgVector, Tika, DashScope API |
| **传输层** | 请求/响应数据封装 | `ChatRequest`, `ChatResponse`, `RagResponse`, `ProviderInfoResponse` |

---

## 四、核心功能实现详解

### 4.1 文档上传与向量化流程

#### 4.1.1 完整处理链路

```
用户上传文件 (MultipartFile)
    │
    ├─ 1. 文件校验
    │   ├─ 空文件检查
    │   └─ 大小限制 (50MB)
    │
    ├─ 2. 文档解析 (Tika)
    │   ├─ InputStreamResource 包装
    │   ├─ TikaDocumentReader 读取
    │   └─ 输出 List<Document> (每页/段一个 Document)
    │
    ├─ 3. 文本分块 (TokenTextSplitter)
    │   ├─ chunkSize: 500 tokens
    │   ├─ minChunkSize: 100 tokens
    │   ├─ overlap: 50 tokens
    │   ├─ maxOverlap: 1000 tokens
    │   └─ 输出 List<Document> (chunk 级别)
    │
    ├─ 4. 元数据注入
    │   ├─ document_id: UUID
    │   ├─ document_name: 文件名
    │   ├─ chunk_index: 块索引
    │   ├─ total_chunks: 总块数
    │   ├─ file_type: MIME 类型
    │   └─ upload_time: 时间戳
    │
    ├─ 5. 批量向量化 (batch size = 10)
    │   ├─ 调用 DashScope Embedding API
    │   ├─ 模型: tongyi-embedding-vision-flash
    │   ├─ 维度: 1024
    │   └─ 分批写入 PgVector (避免 API 限流)
    │
    └─ 6. 返回 document_id
```

#### 4.1.2 关键代码实现

**文档解析** (`DocumentService.java`):

```java
// 使用 Tika 读取文档内容
InputStreamResource resource = new InputStreamResource(file.getInputStream());
TikaDocumentReader reader = new TikaDocumentReader(resource);
List<Document> documents = reader.get();
```

**文本分块**:

```java
// TokenTextSplitter 参数:
// - chunkSize: 500 tokens (每个块的大小)
// - minChunkSize: 100 tokens (最小块大小)
// - overlap: 50 tokens (块间重叠)
// - maxOverlap: 1000 tokens (最大重叠)
// - keepSeparator: true (保留分隔符)
this.textSplitter = new TokenTextSplitter(500, 100, 50, 1000, true);
List<Document> chunks = textSplitter.split(documents);
```

**元数据注入**:

```java
for (int i = 0; i < chunks.size(); i++) {
    Document chunk = chunks.get(i);
    Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
    metadata.put("document_id", documentId);
    metadata.put("document_name", name);
    metadata.put("chunk_index", i);
    metadata.put("total_chunks", chunks.size());
    metadata.put("file_type", file.getContentType());
    metadata.put("upload_time", System.currentTimeMillis());
    chunks.set(i, new Document(chunk.getText(), metadata));
}
```

**批量向量化存储**:

```java
// 分批处理，解决阿里云 API 最大限制 10 的问题
int batchSize = 10;
for (int i = 0; i < chunks.size(); i += batchSize) {
    int endIndex = Math.min(i + batchSize, chunks.size());
    List<Document> batchChunks = chunks.subList(i, endIndex);
    vectorStore.add(batchChunks);
}
```

#### 4.1.3 支持的文档格式

| 格式 | MIME Type | 说明 |
|------|-----------|------|
| PDF | `application/pdf` | 支持多页 PDF |
| Word (.doc) | `application/msword` | 旧版 Word 格式 |
| Word (.docx) | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | 新版 Word |
| Excel (.xls) | `application/vnd.ms-excel` | 旧版 Excel |
| Excel (.xlsx) | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | 新版 Excel |
| PPT (.ppt) | `application/vnd.ms-powerpoint` | 旧版 PPT |
| PPT (.pptx) | `application/vnd.openxmlformats-officedocument.presentationml.presentation` | 新版 PPT |
| TXT | `text/plain` | 纯文本 |
| Markdown | `text/markdown` | Markdown 格式 |
| HTML | `text/html` | HTML 网页 |

**文件大小限制**: 最大 50MB

---

### 4.2 RAG 检索增强生成流程

#### 4.2.1 完整 RAG 管道

```
用户提问
    │
    ├─ 1. 向量检索 (多取策略)
    │   ├─ fetchSize = max(topK * 3, 15)
    │   ├─ 调用 DashScope Embedding API 向量化问题
    │   ├─ PgVector HNSW 索引相似度检索
    │   └─ 返回原始候选列表
    │
    ├─ 2. 质量控制 - 垃圾 chunk 过滤
    │   ├─ 规则1: 长度 < 50 字符 → 丢弃
    │   ├─ 规则2: 纯 URL 列表 → 丢弃
    │   ├─ 规则3: 空白/无意义字符 → 丢弃
    │   ├─ 规则4: URL 行占比 > 文本行 → 丢弃
    │   └─ 输出有效 chunk 列表
    │
    ├─ 3. 二次裁剪
    │   └─ 取前 topK 个有效 chunk
    │
    ├─ 4. 上下文构建
    │   ├─ 格式: [片段 N]\n来源: 文件名\n内容\n\n
    │   └─ 拼接所有 chunk
    │
    ├─ 5. 大模型生成
    │   ├─ System Prompt: RAG 规则模板
    │   ├─ User: 用户问题
    │   └─ 调用 qwen-plus 生成答案
    │
    ├─ 6. 引用合法性校验
    │   ├─ 正则提取答案中的 [片段N]
    │   ├─ 验证编号是否在有效范围内
    │   ├─ 清理伪引用 (替换为 [未标注引用])
    │   └─ 输出清理后的答案 + 有效索引集合
    │
    ├─ 7. Sources 过滤
    │   ├─ 只保留答案中实际引用的片段
    │   └─ 提取每个 source 的 URL 列表
    │
    ├─ 8. 空引用保护
    │   ├─ 如果 sources 为空
    │   └─ 返回: "未在知识库中找到明确依据..."
    │
    └─ 9. 返回结果
        ├─ answer: AI 生成的答案
        └─ sources: 引用来源列表 (含 URL)
```

#### 4.2.2 质量控制管道详解

**多取策略**:

```java
// 多取一些以便过滤后有足够数量
int fetchSize = Math.max(topK * 3, 15);
List<Document> docs = documentService.searchSimilarDocuments(question, fetchSize);
```

**垃圾 chunk 过滤规则**:

```java
private boolean isUseful(Document doc) {
    String text = doc.getText();
    if (text == null || text.isBlank()) return false;
    
    String trimmed = text.trim();
    
    // 规则1: 太短（< 50 字符）→ 没有实质内容
    if (trimmed.length() < 50) return false;
    
    // 规则2: 纯 URL 或导航性内容
    String[] lines = trimmed.split("\n");
    long urlLines = 0;
    long meaningfulLines = 0;
    for (String line : lines) {
        String l = line.trim();
        if (l.isEmpty()) continue;
        if (l.contains("http")) urlLines++;
        else meaningfulLines++;
    }
    // 如果大多数行都是 URL，视为垃圾
    if (meaningfulLines == 0) return false;
    if (urlLines > meaningfulLines) return false;
    
    return true;
}
```

#### 4.2.3 RAG 系统提示词模板

```java
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

【无法回答】
11. 如果参考文档中没有相关信息：
   - 直接回答："未在知识库中找到明确依据，建议换个问法或补充文档。"
   - 不要进行任何推测或扩展

【参考文档】
{context}
""";
```

#### 4.2.4 引用合法性校验

```java
// 正则提取答案中的 [片段N] 引用
Pattern pattern = Pattern.compile("\\[片段\\s*(\\d+)\\]");
Matcher matcher = pattern.matcher(answer);

Set<Integer> referencedIndexes = new HashSet<>();
while (matcher.find()) {
    int index = Integer.parseInt(matcher.group(1));
    referencedIndexes.add(index);
}

// 验证引用编号是否在有效范围内
Set<Integer> validIndexes = new HashSet<>();
for (Integer idx : referencedIndexes) {
    if (idx >= 1 && idx <= sources.size()) {
        validIndexes.add(idx);
    }
}

// 清理伪引用
String cleanedAnswer = answer.replaceAll(
    "\\[片段\\s*\\d+\\]", 
    "[未标注引用]"
);
```

#### 4.2.5 URL 自动提取

```java
private List<String> extractUrls(String text) {
    List<String> urls = new ArrayList<>();
    Pattern pattern = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
        urls.add(matcher.group());
    }
    return urls;
}
```

---

### 4.3 多模型动态路由

#### 4.3.1 架构设计

```
┌─────────────────────────────────────────────────┐
│              application.yml                     │
│                                                  │
│  ragent.ai:                                      │
│    default-provider: bailian                     │
│    providers:                                    │
│      - id: bailian                               │
│        name: 阿里云百炼 Qwen                      │
│        type: openai-compatible                   │
│        base-url: https://dashscope...            │
│        api-key: sk-xxx                           │
│        model: qwen-plus                          │
│        temperature: 0.7                          │
│        max-tokens: 4096                          │
│        enabled: true                             │
│                                                  │
│      - id: bailian-embedding                     │
│        ...                                       │
│                                                  │
│      - id: deepseek (可选)                       │
│        ...                                       │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│           ProviderProperties                     │
│                                                  │
│  @ConfigurationProperties(prefix = "ragent.ai")  │
│  • defaultProvider: String                       │
│  • providers: List<ProviderConfig>               │
│                                                  │
│  ProviderConfig:                                 │
│  • id, name, type, baseUrl                       │
│  • apiKey, model                                 │
│  • temperature, maxTokens, enabled               │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│            ChatClientRouter                      │
│                                                  │
│  Map<String, ChatClient> clientMap               │
│                                                  │
│  initClients():                                  │
│    for each provider:                            │
│      buildClient(config) → clientMap.put(id)     │
│                                                  │
│  buildClient(config):                            │
│    DashScopeApi → DashScopeChatModel             │
│    → ChatClient (with system prompt)             │
│                                                  │
│  getClient(providerId):                          │
│    return clientMap.get(providerId)              │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│            ChatController                        │
│                                                  │
│  POST /api/chat:                                 │
│    provider = resolveProvider(request.provider)  │
│    client = router.getClient(provider)           │
│    response = client.prompt().user(msg).call()   │
│                                                  │
│  POST /api/chat/stream:                          │
│    Flux<String> = client.stream().content()      │
└─────────────────────────────────────────────────┘
```

#### 4.3.2 ChatClient 构建链路

```java
private ChatClient buildClient(ProviderProperties.ProviderConfig config) {
    // 1. 创建 DashScopeApi
    DashScopeApi api = DashScopeApi.builder()
            .apiKey(config.getApiKey())
            .build();

    // 2. 创建 ChatOptions（模型参数）
    DashScopeChatOptions options = DashScopeChatOptions.builder()
            .withModel(config.getModel())
            .withTemperature(config.getTemperature())
            .build();

    // 3. 创建 DashScopeChatModel
    DashScopeChatModel model = DashScopeChatModel.builder()
            .dashScopeApi(api)
            .defaultOptions(options)
            .build();

    // 4. 创建 ChatClient（Fluent API）
    String systemPrompt = "你是一个由 " + config.getName() + " 提供支持的智能助手，请用中文回答问题。";
    
    return ChatClient.builder(model)
            .defaultSystem(systemPrompt)
            .build();
}
```

#### 4.3.3 扩展新模型平台

只需在 `application.yml` 中添加配置即可，**无需修改代码，无需重启**：

```yaml
ragent:
  ai:
    providers:
      - id: new-platform
        name: 新平台名称
        type: openai-compatible
        base-url: https://api.new-platform.com/v1
        api-key: your-api-key
        model: model-name
        temperature: 0.7
        max-tokens: 4096
        enabled: true
```

---

### 4.4 向量存储配置

#### 4.4.1 PgVector 配置

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true       # 自动创建表结构
        dimensions: 1024              # 向量维度 (与 Embedding 模型匹配)
        index-type: HNSW              # 索引类型: HNSW / IVFFLAT
        distance-type: COSINE_DISTANCE # 距离度量: COSINE_DISTANCE / L2_DISTANCE / INNER_PRODUCT
        schema-name: public           # 数据库 schema
        table-name: vector_store      # 向量存储表名
```

#### 4.4.2 HNSW 索引说明

**HNSW (Hierarchical Navigable Small World)** 是一种高效的近似最近邻搜索算法：

| 特性 | 说明 |
|------|------|
| **搜索速度** | 极快，适合实时查询 |
| **内存占用** | 较高，需要维护图结构 |
| **准确性** | 高，接近精确搜索 |
| **适用场景** | 读多写少、对延迟敏感的场景 |

#### 4.4.3 COSINE_DISTANCE 距离度量

余弦距离衡量两个向量的方向相似度：

$$\text{cosine\_distance}(A, B) = 1 - \frac{A \cdot B}{\|A\| \|B\|}$$

- 值域: [0, 2]
- 0 表示完全相同
- 1 表示正交（无关）
- 2 表示完全相反

---

### 4.5 流式输出 (SSE)

#### 4.5.1 后端实现

```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestBody ChatRequest request) {
    String providerId = resolveProvider(request.getProvider());
    ChatClient client = router.getClient(providerId);

    return client.prompt()
            .user(request.getMessage())
            .stream()
            .content();  // 返回 Flux<String>
}
```

#### 4.5.2 前端接收 (Fetch API)

```javascript
const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: userInput })
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    
    const chunk = decoder.decode(value);
    // 处理 SSE 数据
    const lines = chunk.split('\n');
    for (const line of lines) {
        if (line.startsWith('data:')) {
            const content = line.slice(5).trim();
            displayContent(content);
        }
    }
}
```

---

## 五、API 接口文档

### 5.1 聊天接口

#### POST `/api/chat` - 非流式聊天

**请求体**:

```json
{
    "message": "你好，介绍一下你自己",
    "provider": "bailian",
    "systemPrompt": "你是一个专业的技术顾问"
}
```

**响应**:

```json
{
    "content": "你好！我是 RAGent 智能助手...",
    "provider": "bailian"
}
```

#### POST `/api/chat/stream` - 流式聊天

**请求体**: 同上

**响应**: SSE 流式文本

#### GET `/api/providers` - 获取可用模型列表

**响应**:

```json
{
    "defaultProvider": "bailian",
    "providers": [
        {
            "id": "bailian",
            "name": "阿里云百炼 Qwen",
            "model": "qwen-plus",
            "enabled": true
        }
    ]
}
```

### 5.2 文档管理接口

#### POST `/api/documents/upload` - 上传文档

**请求**: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 上传的文件 |
| name | String | 否 | 文档名称（默认使用文件名） |

**响应**:

```json
{
    "success": true,
    "documentId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "员工手册.pdf",
    "message": "文档上传成功，已进行向量化处理"
}
```

#### DELETE `/api/documents/{documentId}` - 删除文档

**响应**:

```json
{
    "success": true,
    "message": "文档删除成功"
}
```

#### GET `/api/documents/supported-types` - 获取支持的文件类型

**响应**:

```json
{
    "supportedTypes": [
        "application/pdf",
        "text/plain",
        "text/markdown",
        "..."
    ],
    "maxSize": "50MB",
    "description": "支持 PDF、Word、Excel、PPT、TXT、Markdown、HTML 等常见文档格式"
}
```

### 5.3 RAG 问答接口

#### POST `/api/rag/chat` - RAG 问答

**请求体**:

```json
{
    "question": "公司的年假政策是什么？",
    "topK": 5,
    "documentId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**响应**:

```json
{
    "success": true,
    "answer": "根据公司政策，员工每年享有 10 天带薪年假 [片段1]...",
    "sources": [
        {
            "snippetIndex": 1,
            "fileName": "员工手册.pdf",
            "content": "第三章 休假政策\n3.1 年假\n...",
            "urls": ["https://hr.company.com/policy/leave"]
        }
    ],
    "question": "公司的年假政策是什么？"
}
```

#### POST `/api/rag/chat/stream` - 流式 RAG 问答

**请求体**: 同上

**响应**: SSE 流式文本

#### POST `/api/rag/search` - 文档片段搜索

**请求体**:

```json
{
    "query": "年假政策",
    "topK": 5
}
```

**响应**:

```json
{
    "success": true,
    "query": "年假政策",
    "results": [
        {
            "text": "第三章 休假政策...",
            "metadata": {
                "document_id": "...",
                "document_name": "员工手册.pdf",
                "chunk_index": 5
            }
        }
    ],
    "total": 5
}
```

---

## 六、数据库设计

### 6.1 PgVector 向量存储表

表名: `public.vector_store` (自动创建)

| 列名 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| content | TEXT | 原始文本内容 |
| metadata | JSONB | 元数据 (document_id, document_name, chunk_index 等) |
| embedding | vector(1024) | 1024 维向量 |

### 6.2 索引

```sql
-- HNSW 索引 (自动创建)
CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);
```

### 6.3 元数据字段

| 字段 | 类型 | 说明 |
|------|------|------|
| document_id | String | 文档唯一 ID (UUID) |
| document_name | String | 文档名称 |
| chunk_index | Integer | 块索引 (从 0 开始) |
| total_chunks | Integer | 文档总块数 |
| file_type | String | MIME 类型 |
| upload_time | Long | 上传时间戳 |

---

## 七、部署与运行

### 7.1 环境要求

| 组件 | 最低版本 | 推荐版本 |
|------|----------|----------|
| Java | 17 | 17+ |
| Maven | 3.6 | 3.8+ |
| Docker | 20.10 | 24+ |
| Docker Compose | 2.0 | 2.20+ |
| PostgreSQL | 16 | 16 |

### 7.2 快速启动

```bash
# 1. 启动 PgVector 数据库
docker compose up -d

# 2. 构建项目
./mvnw clean package -DskipTests

# 3. 运行应用
java -jar target/RAGent-0.0.1-SNAPSHOT.jar

# 4. 访问测试页面
# 浏览器打开: http://localhost:8080/rag-test.html
```

### 7.3 Docker Compose 配置

```yaml
services:
  pgvector:
    image: 'pgvector/pgvector:pg16'
    environment:
      - 'POSTGRES_DB=mydatabase'
      - 'POSTGRES_PASSWORD=secret'
      - 'POSTGRES_USER=myuser'
    labels:
      - "org.springframework.boot.service-connection=postgres"
    ports:
      - '5432'
```

---

## 八、关键技术决策

### 8.1 为什么选择 Spring AI？

| 优势 | 说明 |
|------|------|
| **统一抽象** | 统一的 ChatClient、VectorStore、EmbeddingModel 接口 |
| **多模型支持** | 切换模型只需更换 starter，无需改代码 |
| **Spring 生态** | 与 Spring Boot 无缝集成，自动配置 |
| **Fluent API** | 链式调用，代码简洁 |
| **流式支持** | 原生支持 Reactor Flux 流式输出 |

### 8.2 为什么选择 PgVector？

| 优势 | 说明 |
|------|------|
| **SQL 兼容** | 支持标准 SQL 查询和 metadata 过滤 |
| **HNSW 索引** | 高效的近似最近邻搜索 |
| **事务支持** | ACID 事务保证数据一致性 |
| **生态成熟** | PostgreSQL 生态工具丰富 |
| **Spring AI 集成** | 官方 starter 支持 |

### 8.3 为什么选择 Tika？

| 优势 | 说明 |
|------|------|
| **格式广泛** | 支持 1000+ 文件格式 |
| **Apache 项目** | 成熟稳定，社区活跃 |
| **Spring AI 集成** | TikaDocumentReader 开箱即用 |
| **元数据提取** | 自动提取文档元数据 |

### 8.4 质量控制策略

| 策略 | 目的 | 实现 |
|------|------|------|
| **多取策略** | 为过滤预留余量 | fetchSize = max(topK * 3, 15) |
| **垃圾过滤** | 提升上下文质量 | 长度、URL 占比、空白检测 |
| **引用校验** | 防止 AI 幻觉 | 正则提取 + 范围验证 |
| **空引用保护** | 杜绝编造 | sources 为空时拒绝回答 |
| **URL 提取** | 方便溯源 | 正则提取 http/https 链接 |

---

## 九、性能优化

### 9.1 向量化优化

| 优化点 | 说明 |
|--------|------|
| **批量处理** | batch size = 10，避免 API 限流 |
| **HNSW 索引** | O(log N) 搜索复杂度 |
| **COSINE 距离** | 适合文本语义相似度 |
| **1024 维度** | 平衡精度和性能 |

### 9.2 检索优化

| 优化点 | 说明 |
|--------|------|
| **多取策略** | 检索更多候选，过滤后裁剪 |
| **Metadata 过滤** | 支持 document_id 精确过滤 |
| **分块大小** | 500 tokens + 100 overlap，平衡上下文和精度 |

### 9.3 流式输出优化

| 优化点 | 说明 |
|--------|------|
| **Reactor Flux** | 非阻塞响应式流 |
| **SSE 协议** | 浏览器原生支持，无需额外库 |
| **逐字输出** | 降低首字延迟 |

---

## 十、安全考虑

### 10.1 API Key 管理

- API Key 存储在 `application.yml` 中
- 生产环境建议使用环境变量或密钥管理服务
- 不同 provider 使用独立的 API Key

### 10.2 文件上传安全

- 文件大小限制: 50MB
- 空文件校验
- 异常捕获和日志记录

### 10.3 跨域配置

```java
@CrossOrigin(origins = "*")
```

生产环境建议限制具体的域名。

---

## 十一、未来扩展方向

### 11.1 已规划功能

| 功能 | 状态 | 说明 |
|------|------|------|
| Rerank 重排序 | 配置就绪 | 使用 qwen3-vl-rerank 提升检索精度 |
| 多模型对比 | 可扩展 | 同时调用多个模型，对比结果 |
| 对话历史 | 待实现 | 支持多轮对话上下文 |
| 文档管理 UI | 待实现 | 文档列表、搜索、预览 |

### 11.2 技术优化方向

| 方向 | 说明 |
|------|------|
| **混合检索** | 向量检索 + 关键词检索 (BM25) |
| **查询改写** | 自动优化用户查询 |
| **缓存机制** | 缓存常见问题的答案 |
| **权限控制** | 文档级别的访问权限 |
| **监控告警** | API 调用统计、错误率监控 |

---

## 十二、常见问题

### Q1: 如何切换 Embedding 模型？

修改 `application.yml` 中的 `spring.ai.dashscope` 配置，确保 `dimensions` 与模型输出维度匹配。

### Q2: 如何添加新的模型平台？

在 `application.yml` 的 `ragent.ai.providers` 中添加新配置即可，无需修改代码。

### Q3: 向量数据库数据存在哪里？

默认存储在 PostgreSQL 的 `public.vector_store` 表中。

### Q4: 如何清理向量数据？

调用 `DELETE /api/documents/{documentId}` 接口，系统会自动删除该文档的所有向量。

### Q5: 流式输出和非流式输出有什么区别？

- **非流式**: 等待模型生成完整答案后返回，适合需要 sources 的场景
- **流式**: 逐字输出，首字延迟低，适合对话场景

---

## 十三、参考资料

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Alibaba 文档](https://sca.aliyun.com/docs/next/ai/overview/)
- [PgVector GitHub](https://github.com/pgvector/pgvector)
- [Apache Tika 文档](https://tika.apache.org/)
- [阿里云百炼平台](https://help.aliyun.com/zh/model-studio/)

---

**文档版本**: v1.0  
**最后更新**: 2026年5月13日  
**维护者**: Jawn Team
