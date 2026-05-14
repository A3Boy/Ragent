# RAGent 开发技能

## 概述

这些技能用于自动化 RAGent 项目的常见开发任务，包括添加新的 AI 提供商、创建文档管理功能、设置测试等。

## 技能列表

### 1. add-ai-provider

**用途**: 添加新的 AI 提供商支持

**使用场景**: 当需要集成新的 AI 平台（如 OpenAI、Claude 等）时使用

**生成的文件**:
- `src/main/resources/application-{provider}.yml` - 新 AI 提供商配置文件
- 更新 `AGENTS.md` 文档

**示例用法**:
```
/create-skill add-ai-provider --provider=openai --model=gpt-4 --embedding=text-embedding-3-small
```

### 2. create-document-controller

**用途**: 创建文档管理控制器

**使用场景**: 需要添加文档上传、解析、向量化功能时使用

**生成的文件**:
- `src/main/java/com/jawn/ragent/controller/DocumentController.java`
- `src/main/java/com/jawn/ragent/entity/Document.java`
- `src/main/java/com/jawn/ragent/service/DocumentService.java`

**示例用法**:
```
/create-skill create-document-controller --formats=pdf,docx,txt,md
```

### 3. add-knowledge-base

**用途**: 添加知识库管理功能

**使用场景**: 需要实现知识库的创建、查询、删除功能时使用

**生成的文件**:
- `src/main/java/com/jawn/ragent/entity/KnowledgeBase.java`
- `src/main/java/com/jawn/ragent/controller/KnowledgeBaseController.java`
- `src/main/java/com/jawn/ragent/service/KnowledgeBaseService.java`

**示例用法**:
```
/create-skill add-knowledge-base --vector-dimensions=1024 --distance-type=COSINE
```

### 4. create-test-suite

**用途**: 创建测试套件

**使用场景**: 需要为聊天功能、文档处理等添加单元测试和集成测试时使用

**生成的文件**:
- `src/test/java/com/jawn/ragent/controller/ChatControllerTest.java`
- `src/test/java/com/jawn/ragent/service/DocumentServiceTest.java`
- `src/test/resources/application-test.yml`

**示例用法**:
```
/create-skill create-test-suite --include=integration,unit
```

### 5. setup-docker-production

**用途**: 设置生产环境 Docker 配置

**使用场景**: 需要为生产环境创建 Docker 配置时使用

**生成的文件**:
- `Dockerfile`
- `docker-compose.prod.yml`
- `.dockerignore`

**示例用法**:
```
/create-skill setup-docker-production --port=8080 --jvm-args="-Xmx2g -Xms1g"
```

### 6. add-authentication

**用途**: 添加用户认证功能

**使用场景**: 需要为 API 添加 JWT 认证时使用

**生成的文件**:
- `src/main/java/com/jawn/ragent/config/SecurityConfig.java`
- `src/main/java/com/jawn/ragent/controller/AuthController.java`
- `src/main/java/com/jawn/ragent/entity/User.java`
- `src/main/java/com/jawn/ragent/service/AuthService.java`

**示例用法**:
```
/create-skill add-authentication --provider=jwt --expiration=86400
```

## 技能使用指南

### 通用参数
- `--provider`: AI 提供商名称
- `--model`: 使用的 AI 模型
- `--formats`: 支持的文档格式（逗号分隔）
- `--dimensions`: 向量维度
- `--distance-type`: 距离算法类型

### 技能依赖
某些技能可能需要先执行其他技能：
- `add-knowledge-base` 依赖于 `create-document-controller`
- `add-authentication` 依赖于基本的控制器结构

### 配置管理
所有技能都会自动更新相关的配置文件和文档，确保项目的一致性。

## 扩展技能

可以根据项目需求创建新的技能，例如：
- `add-rag-pipeline` - 创建完整的 RAG 处理管道
- `add-metrics` - 添加应用监控和指标收集
- `add-caching` - 添加 Redis 缓存支持
- `add-logging` - 配置结构化日志记录  

## 技能最佳实践

1. **渐进式开发**: 从简单的技能开始，逐步添加复杂功能
2. **保持一致性**: 确保所有技能生成的代码遵循项目约定
3. **文档更新**: 每次使用技能后，及时更新相关文档
4. **测试验证**: 使用 `create-test-suite` 技能为新功能创建测试