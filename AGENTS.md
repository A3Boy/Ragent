# RAGent - 企业级 RAG 多智能体知识库

## 项目概述

RAGent 是一个基于 Spring Boot 3.3.5 + Spring AI 1.1.5 的企业级知识库智能问答系统，支持多个 AI 平台（阿里云百炼、 GLM、DeepSeek）。

## 技术栈

- **框架**: Spring Boot 3.3.5
- **语言**: Java 21
- **AI 框架**: Spring AI 1.1.5
- **数据库**: PostgreSQL + PgVector 向量数据库
- **文档解析**: Apache Tika
- **构建工具**: Maven

## 核心功能

### 聊天接口
- **普通聊天**: `POST /api/chat` - 非流式响应
- **流式聊天**: `POST /api/chat/stream` - SSE 流式响应，类似 ChatGPT 体验
- **跨域支持**: 默认允许所有来源的跨域请求

### AI 平台支持
- ** AI** (默认): `glm-4.5-air` 模型
- **阿里云百炼**: `qwen-plus` 模型
- **DeepSeek**: 支持 OpenAI 兼容模式

## 项目结构

```
src/main/java/com/jawn/ragent/
├── RaGentApplication.java          # 主启动类
└── controller/
    └── ChatController.java         # 聊天控制器
```

## 配置文件

### 主配置
- `application.yml` - 主配置文件，包含数据库和向量存储配置
- `application-zhipu.yml` -  AI 配置
- `application-bailian.yml` - 阿里云百炼配置

### 数据库配置
- **数据库**: PostgreSQL + PgVector
- **端口**: 5432
- **数据库名**: ragent_db
- **向量存储**: 1024 维度，HNSW 索引，余弦距离

## 构建和运行

### 开发环境
```bash
# 使用 Maven Wrapper 构建
./mvnw clean compile

# 运行应用
./mvnw spring-boot:run

# 指定 profile 运行
./mvnw spring-boot:run -Dspring-boot.run.profiles=zhipu
```

### Docker Compose
```bash
# 启动所有服务（包括 pgvector）
docker-compose up -d
```

## 开发约定

### 代码约定
1. **RESTful API**: 使用标准 REST 接口设计
2. **ai驱动**: 在 Controller 中通过请求参数动态选择使用哪个 ChatClient在 Controller 中通过请求参数动态选择使用哪个 ChatClient
3. **跨域支持**: 默认允许所有来源的跨域请求
4. **流式响应**: 优先使用 SSE 流式接口

### 配置约定
1. **文件命名**: `application-{provider}.yml` 用于不同 AI 提供商
2. **默认配置**: 默认使用 AI (zhipu profile)
3. **API 密钥**: 所有 API 密钥都配置在各自的配置文件中

## 扩展指南

### 添加新的 AI 提供商
1. 创建新的配置文件: `application-{provider}.yml`
2. 在主配置文件中添加对应的 profile
3. 配置相应的 base URL 和 API 密钥

### 添加文档解析功能
项目已集成 Apache Tika，支持多种文档格式：
- PDF
- Word (.docx)
- TXT
- Markdown

### 添加知识库管理
需要添加以下组件：
1. **实体类**: 定义知识库和文档相关实体
2. **服务层**: 处理文档上传、向量化、检索等业务逻辑
3. **控制器**: 提供文档管理 API

## 常见问题

### 数据库连接问题
确保 PostgreSQL 服务正在运行，并且连接配置正确：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ragent_db
    username: postgres
    password: 123456
```

### AI 平台切换
通过修改 `application.yml` 中的 `spring.profiles.active` 来切换不同的 AI 平台：
```yaml
spring:
  profiles:
    active: zhipu  # 或 bailian
```

## 相关链接

- [Spring Boot 文档](https://docs.spring.io/spring-boot/)
- [Spring AI 文档](https://docs.spring.io/spring-ai/)
- [PgVector 文档](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [Docker Compose 配置](compose.yaml)