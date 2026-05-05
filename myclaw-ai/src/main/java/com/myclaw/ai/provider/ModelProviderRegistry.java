package com.myclaw.ai.provider;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ModelProviderRegistry {

    private final Map<String, ModelProvider> providers = new ConcurrentHashMap<>();

    public void register(ModelProvider provider) {
        providers.put(provider.getProviderId(), provider);
    }

    public ModelProvider resolve(String modelRef, String apiFormat) {
        // apiFormat takes precedence: "openai", "anthropic"
        if (apiFormat != null && !apiFormat.isEmpty()) {
            ModelProvider p = providers.get(apiFormat);
            if (p != null) return p;
        }
        // Fallback to modelRef prefix: "provider/model"
        String providerId = "openai";
        if (modelRef != null && modelRef.contains("/")) {
            providerId = modelRef.substring(0, modelRef.indexOf('/'));
        }
        return providers.get(providerId);
    }
}
