package com.jawn.ragent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应 DTO（非流式）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** AI 生成的回复内容 */
    private String content;

    /** 本次使用的 provider ID */
    private String provider;

}