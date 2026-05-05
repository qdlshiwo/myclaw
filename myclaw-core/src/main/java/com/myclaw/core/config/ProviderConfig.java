package com.myclaw.core.config;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderConfig {
    private String apiKey;
    private String baseUrl;
    private String model;
    private String apiFormat; // "openai", "anthropic"
}
