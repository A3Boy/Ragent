# RAGent

## 多租户隔离设计

请求链路：登录签发 JWT（sub=userId） → 拦截器校验 token 并注入 userId → Controller 用 `@RequestAttribute` 取当前用户 → 知识库查询强制 `findByIdAndUserId` 双条件过滤 → 会话历史按 `(userId, kbId)` 读写 → 向量检索按 `knowledge_base_id` 元数据过滤。任意一步未通过归属校验即返回 404，用户无法触达不属于自己的数据。
