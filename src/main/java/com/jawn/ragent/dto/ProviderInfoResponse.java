package com.jawn.ragent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Provider 信息响应 DTO
 *
 * 用于 /api/providers 接口返回当前所有可用模型的列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderInfoResponse {

    /** 默认 provider ID */
    private String defaultProvider;

    /** 所有可用的 provider 列表 */
    private List<ProviderItem> providers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderItem {
        private String id;
        private String name;
        private String model;
        private boolean enabled;
    }
}