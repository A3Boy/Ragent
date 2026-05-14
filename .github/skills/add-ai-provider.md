# 添加 AI 提供商技能

## 技能概述

此技能用于为 RAGent 项目添加新的 AI 提供商支持，自动生成相应的配置文件和更新相关文档。

## 使用场景

当需要集成新的 AI 平台（如 OpenAI、Claude、Anthropic 等）时使用此技能。

## 参数说明

### 必需参数
- `--provider`: AI 提供商名称（如 openai、claude、anthropic）
- `--model`: 默认使用的 AI 模型名称
- `--base-url`: API 基础 URL（OpenAI 兼容模式）
- `--api-key`: API 密钥（用于生成配置文件）

### 可选参数
- `--embedding-model`: 嵌入模型名称（默认与主模型相同）
- `--temperature`: 默认温度值（默认 0.7）
- `--max-tokens`: 最大令牌数（默认 4096）
- `--profile-name`: Profile 名称（默认为提供商名称）

## 使用示例

### 添加 OpenAI 支持
```bash
/create-skill add-ai-provider --provider=openai --model=gpt-4 --base-url=https://api.openai.com/v1 --api-key=your-api-key
```

### 添加 Claude 支持
```bash
/create-skill add-ai-provider --provider=anthropic --model=claude-3-sonnet-20240229 --base-url=https://api.anthropic.com --api-key=your-api-key --embedding-model=claude-3-sonnet-20240229
```

### 添加自定义 AI 平台
```bash
/create-skill add-ai-provider --provider=custom-ai --model=custom-model --base-url=https://api.custom-ai.com/v1 --api-key=your-api-key --temperature=0.5
```

## 生成的文件

### 1. 配置文件
在 `src/main/resources/` 目录下创建：
```
application-{provider}.yml
```

配置文件内容示例：
```yaml
spring:
  ai:
    openai:
      base-url: {base-url}
      api-key: {api-key}
      
      chat:
        options:
          model: {model}
          temperature: {temperature}
          max-tokens: {max-tokens}
          
      embedding:
        options:
          model: {embedding-model}
```

### 2. 文档更新
自动更新以下文档：
- `AGENTS.md` - 添加新的 AI 提供商说明
- `SKILLS.md` - 更新技能列表（如果适用）

## 技能执行流程

### 1. 参数验证
- 验证必需参数是否存在
- 检查配置文件是否已存在
- 验证 API 密钥格式（如果提供）

### 2. 生成配置文件
- 创建新的配置文件
- 填充相应的配置内容
- 设置默认值（如果未提供可选参数）

### 3. 更新文档
- 更新项目文档
- 添加新的 AI 提供商说明
- 更新相关配置示例

### 4. 验证测试
- 检查生成的配置文件语法
- 验证文档更新完整性
- 提供使用说明

## 注意事项

### 安全性
- API 密钥应该存储在环境变量中，而不是硬编码在配置文件中
- 建议使用 `spring.config.import` 来加载外部配置
- 配置文件应该添加到 `.gitignore` 中

### 兼容性
- 确保新的 AI 提供商支持 OpenAI 兼容的 API 格式
- 验证嵌入模型的向量维度是否与现有配置兼容
- 测试流式响应功能是否正常工作

### 性能考虑
- 根据模型特性调整温度和最大令牌数
- 考虑添加请求超时配置
- 优化并发请求处理

## 故障排除

### 常见问题

#### 配置文件生成失败
- 检查文件权限
- 确认目录存在
- 验证参数格式

#### API 调用失败
- 检查 API 密钥有效性
- 验证网络连接
- 确认模型可用性

#### 文档更新失败
- 检查文件锁定状态
- 确认文档格式正确
- 验证写入权限

### 调试步骤

1. **验证参数**
   ```bash
   echo "Provider: $provider, Model: $model, Base URL: $base-url"
   ```

2. **检查生成的配置**
   ```bash
   cat src/main/resources/application-{provider}.yml
   ```

3. **测试 API 连接**
   ```bash
   curl -X POST {base-url}/chat/completions -H "Authorization: Bearer {api-key}" -H "Content-Type: application/json" -d '{"model": "{model}", "messages": [{"role": "user", "content": "Hello"}]}'
   ```

## 最佳实践

### 1. 配置管理
- 使用环境变量存储敏感信息
- 为不同环境创建不同的配置文件
- 使用 Spring Profile 管理多环境配置

### 2. 测试策略
- 为新的 AI 提供商编写集成测试
- 测试流式和非流式响应
- 验证错误处理机制

### 3. 文档维护
- 及时更新 API 使用说明
- 添加配置示例
- 更新故障排除指南

## 相关技能

- `create-document-controller` - 创建文档管理控制器
- `add-knowledge-base` - 添加知识库管理功能
- `create-test-suite` - 创建测试套件