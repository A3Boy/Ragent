package com.jawn.ragent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * ChatClient 动态路由核心类
 *
 * 统一使用 OpenAI 兼容模式（openai-compatible）：
 *   OpenAiApi → OpenAiChatModel → ChatClient
 *
 * 支持 DeepSeek、DashScope 兼容模式等所有 OpenAI 兼容 API。
 */
@Configuration
public class ChatClientRouter {

    private final Map<String, ChatClient> clientMap = new HashMap<>();
    private final Map<String, String> modelTypeMap = new HashMap<>();
    private final ProviderProperties properties;

    public ChatClientRouter(ProviderProperties properties) {
        this.properties = properties;
        initClients();
    }

    /**
     * 初始化所有已配置的 ChatClient
     */
    private void initClients() {
        if (properties.getProviders() == null) {
            return;
        }

        for (ProviderProperties.ProviderConfig config : properties.getProviders()) {
            if (!config.isEnabled()) {
                continue;
            }
            ChatClient client = buildClient(config);
            clientMap.put(config.getId(), client);
            modelTypeMap.put(config.getId(), config.getModelType() != null ? config.getModelType() : "chat");
        }
    }

    /**
     * 根据 ProviderConfig 构建 ChatClient（仅支持 openai-compatible 类型）
     */
    private ChatClient buildClient(ProviderProperties.ProviderConfig config) {
        if (!"openai-compatible".equals(config.getType())) {
            throw new IllegalArgumentException(
                    "不支持的 provider type: " + config.getType() + "，仅支持 openai-compatible");
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModel())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();

        String systemPrompt = StringUtils.hasText(config.getName())
                ? "你是一个由 " + config.getName() + " 提供支持的智能助手，请用中文回答问题。"
                : "你是一个智能助手，请用中文回答问题。";

        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();
    }

    /**
     * 根据 provider ID 获取对应的 ChatClient
     *
     * @param providerId provider ID，如 bailian、deepseek
     * @return 对应的 ChatClient
     * @throws IllegalArgumentException 如果 provider 不存在或未启用
     */
    public ChatClient getClient(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            providerId = properties.getDefaultProvider();
        }

        ChatClient client = clientMap.get(providerId);
        if (client == null) {
            throw new IllegalArgumentException(
                    "未找到或未启用的 provider: " + providerId +
                    "，当前可用的: " + clientMap.keySet());
        }
        return client;
    }

    /**
     * 获取所有已注册且已启用的 provider ID 列表
     */
    public Map<String, ChatClient> getAllClients() {
        return clientMap;
    }

    /**
     * 获取指定 provider 的模型类型（chat 或 reasoner）
     *
     * @param providerId provider ID，null 则使用默认 provider
     * @return modelType，如 "chat" 或 "reasoner"
     */
    public String getClientModelType(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            providerId = properties.getDefaultProvider();
        }
        return modelTypeMap.getOrDefault(providerId, "chat");
    }

    /**
     * 获取指定 provider 的完整配置（用于 raw HTTP 调用等场景）
     *
     * @param providerId provider ID，null 则使用默认 provider
     * @return ProviderConfig，找不到则返回 null
     */
    public ProviderProperties.ProviderConfig getProviderConfig(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            providerId = properties.getDefaultProvider();
        }
        return properties.getProvider(providerId);
    }
}
