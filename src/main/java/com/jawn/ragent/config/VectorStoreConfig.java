package com.jawn.ragent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置 - 本地 PgVector + DashScope Embedding
 * 
 * EmbeddingModel 由 Spring AI Alibaba 自动配置（基于 spring.ai.dashscope.api-key）。
 */
@Configuration
public class VectorStoreConfig {
    // EmbeddingModel Bean 已由 Spring AI Alibaba 自动装配，无需手动创建
}