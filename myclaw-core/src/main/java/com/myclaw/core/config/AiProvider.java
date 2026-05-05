package com.myclaw.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProvider {
    private String id;
    private String name;
    private String baseUrl;
    private String apiKey;
    private String apiFormat; // "openai", "anthropic"
    private List<ModelInfo> models;
    private String currentModel;
    private boolean enabled;

    public ProviderConfig toProviderConfig() {
        return ProviderConfig.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .model(currentModel)
            .apiFormat(apiFormat)
            .build();
    }
}
