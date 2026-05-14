package com.jawn.ragent.controller;

import com.jawn.ragent.config.ChatClientRouter;
import com.jawn.ragent.config.ProviderProperties;
import com.jawn.ragent.dto.ChatRequest;
import com.jawn.ragent.dto.ChatResponse;
import com.jawn.ragent.dto.ProviderInfoResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.stream.Collectors;

/**
 * 聊天控制器 - 支持动态多模型路由
 *
 * 提供三个核心接口：
 *   POST /api/chat          - 非流式聊天（同步返回完整结果）
 *   POST /api/chat/stream   - SSE 流式聊天（类似 ChatGPT 体验）
 *   GET  /api/providers     - 查看当前可用的所有模型平台
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClientRouter router;
    private final ProviderProperties properties;

    public ChatController(ChatClientRouter router, ProviderProperties properties) {
        this.router = router;
        this.properties = properties;
    }

    /**
     * 非流式聊天接口
     *
     * 请求体示例：
     * {
     *   "message": "你好，介绍一下你自己",
     *   "provider": "bailian",    // 可选，不传则使用默认模型
     *   "systemPrompt": "..."     // 可选，覆盖默认系统提示词
     * }
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String providerId = resolveProvider(request.getProvider());
        ChatClient client = router.getClient(providerId);
        ProviderProperties.ProviderConfig config = properties.getProvider(providerId);

        String content;
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            // 自定义系统提示词：覆盖默认
            content = client.prompt()
                    .system(request.getSystemPrompt())
                    .user(request.getMessage())
                    .call()
                    .content();
        } else {
            // 使用默认系统提示词
            content = client.prompt()
                    .user(request.getMessage())
                    .call()
                    .content();
        }

        return new ChatResponse(content, config.getId());
    }

    /**
     * 流式聊天接口（SSE）
     *
     * 返回 Flux<String>，前端通过 EventSource 或 fetch + ReadableStream 接收，
     * 实现类似 ChatGPT 的逐字输出体验。
     *
     * 请求体与 /api/chat 相同。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        String providerId = resolveProvider(request.getProvider());
        ChatClient client = router.getClient(providerId);

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            return client.prompt()
                    .system(request.getSystemPrompt())
                    .user(request.getMessage())
                    .stream()
                    .content();
        } else {
            return client.prompt()
                    .user(request.getMessage())
                    .stream()
                    .content();
        }
    }

    /**
     * 获取当前所有可用的模型平台信息
     *
     * 前端可调用此接口展示模型选择下拉框。
     */
    @GetMapping("/providers")
    public ProviderInfoResponse getProviders() {
        var items = properties.getProviders().stream()
                .map(p -> new ProviderInfoResponse.ProviderItem(
                        p.getId(), p.getName(), p.getModel(), p.isEnabled()))
                .collect(Collectors.toList());

        return new ProviderInfoResponse(properties.getDefaultProvider(), items);
    }

    /**
     * 解析 provider 参数：空值时使用默认 provider
     */
    private String resolveProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return properties.getDefaultProvider();
        }
        return provider;
    }
}