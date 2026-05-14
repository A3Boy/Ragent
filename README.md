# RAGent

基于 Spring AI + PgVector 的企业级 RAG（检索增强生成）知识库问答系统，支持多模型动态路由和多租户隔离。

## 技术栈

**后端**
- Spring Boot 3.3.5 + Java 17
- Spring AI 1.1.0 + Spring AI Alibaba 1.1.2.0
- PgVector（PostgreSQL 向量扩展，1024 维 HNSW 索引）
- Apache Tika（多格式文档解析）
- JWT 认证 + BCrypt 密码加密

**前端**（[RagentFront](https://github.com/A3Boy/RagentFront)）
- Vue 3.5 + TypeScript + Vite 8
- Pinia 状态管理 + Naive UI 组件库

**AI 模型**
- DeepSeek V4（默认）
- 阿里云百炼 Qwen（兼容模式）
- 向量嵌入：DashScope tongyi-embedding-vision-flash

## 功能特性

- **RAG 智能问答**：基于知识库的问答，自动引用来源片段
- **多模型路由**：通过配置动态切换 DeepSeek / 阿里云百炼等模型
- **SSE 流式响应**：实时打字机效果的流式输出
- **多租户隔离**：JWT 认证 + userId 数据隔离
- **多格式文档**：支持 PDF、Word、Excel、PPT、Markdown、HTML 等
- **对话历史**：多轮对话上下文管理

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- Docker（用于 PostgreSQL）

### 1. 启动数据库

```bash
docker compose up -d
```

### 2. 配置 API Key

复制示例配置并填入你的 API Key：

```bash
cp src/main/resources/application-example.yml src/main/resources/application.yml
```

编辑 `application.yml`，填入：
- `spring.datasource.password`：数据库密码
- `spring.ai.dashscope.api-key`：DashScope API Key（[获取地址](https://dashscope.console.aliyun.com/)）
- `ragent.ai.providers[].api-key`：各模型平台的 API Key

### 3. 启动后端

```bash
cd RAGent
mvn spring-boot:run
```

后端运行在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd RagentFront
npm install
npm run dev
```

前端运行在 `http://localhost:5173`。

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/knowledge-bases` | 创建知识库 |
| POST | `/api/documents/upload` | 上传文档 |
| POST | `/api/rag/chat` | RAG 问答 |
| POST | `/api/rag/chat/stream` | RAG 流式问答 |
| POST | `/api/rag/search` | 向量搜索 |
| GET | `/api/rag/history/{kbId}` | 获取对话历史 |

## 项目结构

```
RAGent/
├── src/main/java/com/jawn/ragent/
│   ├── config/          # 配置类（ChatClientRouter, VectorStore, JWT）
│   ├── controller/      # REST 控制器
│   ├── dto/             # 数据传输对象
│   ├── entity/          # JPA 实体
│   ├── interceptor/     # JWT 拦截器
│   ├── repository/      # 数据访问层
│   └── service/         # 业务逻辑（RAG, Document, Auth）
├── src/main/resources/
│   ├── application.yml           # 配置文件（本地，不提交）
│   └── application-example.yml   # 配置模板
└── compose.yaml         # Docker Compose（PostgreSQL + PgVector）
```

## License

MIT
