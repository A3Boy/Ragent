# RAGent RAG 功能使用指南

## 功能概述

RAGent 现在支持完整的 RAG（检索增强生成）功能：

1. **文档上传** → 自动向量化存储到 PgVector
2. **智能问答** → 基于文档内容回答问题
3. **多模型支持** → Chat + Embedding + Rerank

## 技术架构

```
用户上传文档 
  ↓
DocumentService (文档解析)
  ↓
EmbeddingModel (text-embedding-v3 向量化)
  ↓
PgVector (向量数据库存储)
  ↓
用户提问
  ↓
向量检索 (相似度搜索)
  ↓
Rerank 模型 (可选，qwen3-vl-rerank 重排序)
  ↓
Chat 模型 (qwen-plus 生成答案)
  ↓
返回答案
```

## API 接口

### 1. 上传文档

**接口**: `POST /api/rag/upload`

**Content-Type**: `multipart/form-data`

**请求示例** (使用 curl):
```bash
curl -X POST http://localhost:8080/api/rag/upload \
  -F "file=@/path/to/your/document.pdf"
```

**或使用 Postman**:
- Method: POST
- URL: http://localhost:8080/api/rag/upload
- Body → form-data:
  - Key: `file`, Type: File, Value: 选择文件

**响应示例**:
```json
{
  "success": true,
  "message": "文档上传成功",
  "documentCount": 1,
  "filename": "document.pdf"
}
```

---

### 2. RAG 智能问答（POST）

**接口**: `POST /api/rag/chat`

**Content-Type**: `application/json`

**请求体**:
```json
{
  "message": "公司的报销标准是什么？",
  "topK": 3,
  "useRerank": false
}
```

**参数说明**:
- `message`: 用户问题（必填）
- `topK`: 检索的文档数量，默认 3（可选）
- `useRerank`: 是否使用 Rerank 模型重排序，默认 false（可选）

**响应示例**:
```json
{
  "content": "根据文档，公司的报销标准如下：...",
  "provider": "bailian",
  "model": "qwen-plus + RAG"
}
```

---

### 3. RAG 智能问答（GET 快速测试）

**接口**: `GET /api/rag/chat?q=你的问题`

**请求示例**:
```bash
curl "http://localhost:8080/api/rag/chat?q=公司的报销标准是什么"
```

**响应示例**:
```json
{
  "question": "公司的报销标准是什么？",
  "answer": "根据文档，公司的报销标准如下：..."
}
```

---

## 完整测试流程

### 步骤 1: 启动应用

```bash
cd d:\web\RAGent\RAGent
.\mvnw.cmd spring-boot:run
```

确保 PostgreSQL 数据库已启动，并且创建了 `ragent_db` 数据库。

### 步骤 2: 上传文档

```bash
# 上传一个文本文件
echo "公司的报销标准：
1. 差旅费：高铁二等座，飞机经济舱
2. 住宿费：一线城市不超过 500 元/天
3. 餐饮补贴：200 元/天" > test.txt

curl -X POST http://localhost:8080/api/rag/upload \
  -F "file=@test.txt"
```

### 步骤 3: 提问

```bash
curl -X POST http://localhost:8080/api/rag/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "差旅费报销标准是什么？",
    "topK": 3
  }'
```

**预期响应**:
```json
{
  "content": "根据文档内容，差旅费报销标准为：高铁二等座，飞机经济舱...",
  "provider": "bailian",
  "model": "qwen-plus + RAG"
}
```

---

## 配置文件说明

### application.yml 关键配置

```yaml
ragent:
  ai:
    providers:
      # Chat 模型
      - id: bailian
        model: qwen-plus
        
      # Embedding 模型（向量化）
      - id: bailian-embedding
        model: text-embedding-v3
        
      # Rerank 模型（重排序）
      - id: bailian-rerank
        model: qwen3-vl-rerank

spring:
  ai:
    vectorstore:
      pgvector:
        dimensions: 1024  # 与 text-embedding-v3 的维度匹配
        index-type: HNSW
        distance-type: COSINE_DISTANCE
```

---

## 支持的文档格式

得益于 Apache Tika，系统支持多种文档格式：

- ✅ 文本文件：`.txt`, `.md`, `.csv`
- ✅ Office 文档：`.docx`, `.xlsx`, `.pptx`
- ✅ PDF 文件：`.pdf`
- ✅ 网页文件：`.html`
- ✅ 压缩文件：`.zip` (会提取内部文档)

---

## 性能优化建议

1. **调整 topK**: 
   - 文档量大时，减少 topK（如 3-5）
   - 文档量小时，增加 topK（如 5-10）

2. **启用 Rerank**:
   - 对精度要求高时启用 `useRerank: true`
   - 会增加一次 API 调用，但能显著提高相关性

3. **文档分块**:
   - 大文档会自动分块
   - 可在 DocumentService 中调整分块大小

4. **向量索引**:
   - PgVector 使用 HNSW 索引
   - 文档量大时性能更好

---

## 常见问题

### Q: 上传文档后找不到内容？
A: 检查以下几点：
1. 确认 Embedding API Key 有效
2. 检查 PgVector 数据库连接
3. 查看日志中是否有向量化失败的错误

### Q: 回答不准确？
A: 尝试：
1. 增加 `topK` 参数，检索更多文档
2. 启用 `useRerank` 进行重排序
3. 确保上传的文档包含相关信息

### Q: 如何清空向量数据库？
A: 执行 SQL:
```sql
TRUNCATE TABLE vector_store;
```

---

## 下一步扩展

1. **多文档管理**: 添加文档列表、删除功能
2. **会话记忆**: 支持多轮对话
3. **引用标注**: 答案中标注来源文档
4. **Rerank 集成**: 完整实现阿里云百炼的 Rerank API 调用

祝你使用愉快！🎉