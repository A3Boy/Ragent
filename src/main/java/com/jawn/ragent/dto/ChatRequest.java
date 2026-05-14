package com.jawn.ragent.dto;

import lombok.Data;

/**
 * 聊天请求 DTO
 *
 * 支持通过 provider 参数动态选择大模型平台，
 * 不传 provider 则使用 application.yml 中配置的默认平台。
 */
@Data
public class ChatRequest {

    /** 用户的输入内容 */
    private String message;

    /** 模型平台 ID，如 zhipu、bailian、deepseek */
    private String provider;

    /** 自定义系统提示词（可选，覆盖默认） */
    private String systemPrompt;

    /** RAG 检索的文档数量（默认 3） */
    private Integer topK = 3;

    /** 是否使用 Rerank 模型重新排序（默认 false） */
    private Boolean useRerank = false;
}