package com.jawn.ragent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 配置 - 配置 RAG 服务所需的 ChatClient
 */
@Configuration
public class RagConfig {

    private final ChatClientRouter router;

    public RagConfig(ChatClientRouter router) {
        this.router = router;
    }

    /**
     * 配置用于 RAG 的 ChatClient
     * 使用默认 provider
     */
    @Bean
    public ChatClient ragChatClient() {
        return router.getClient(null);  // null 表示使用默认 provider
    }
}
