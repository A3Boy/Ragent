package com.jawn.ragent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAGent 多模型动态路由配置属性
 *
 * 从 application.yml 中读取 ragent.ai 下的配置，
 * 包含默认 provider 和多个 provider 列表。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ragent.ai")
public class ProviderProperties {

    /** 默认使用的 provider ID */
    private String defaultProvider = "zhipu";

    /** 所有已注册的 provider 列表 */
    private List<ProviderConfig> providers;

    /**
     * 根据 provider ID 查找对应的配置
     */
    public ProviderConfig getProvider(String id) {
        return providers.stream()
                .filter(p -> p.getId().equals(id) && p.isEnabled())
                .findFirst()
                .orElse(null);
    }

    /**
     * 单个 Provider 的配置项
     */
    @Data
    public static class ProviderConfig {
        /** Provider 唯一标识，如 zhipu、bailian、deepseek */
        private String id;
        /** 显示名称 */
        private String name;
        /** 连接类型，目前全部使用 openai-compatible */
        private String type;
        /** API 基础 URL */
        private String baseUrl;
        /** API Key */
        private String apiKey;
        /** 模型名称 */
        private String model;
        /** 温度参数 */
        private Double temperature = 0.7;
        /** 最大输出 Token */
        private Integer maxTokens = 4096;
        /** 是否启用 */
        private boolean enabled = true;
        /** 模型类型：chat（通用对话）或 reasoner（思考模式） */
        private String modelType = "chat";
    }
}