package com.myclaw.server.config;

import com.myclaw.core.config.AiProvider;
import com.myclaw.core.config.ModelInfo;
import com.myclaw.core.config.ProviderConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProviderStore {

    private final Map<String, AiProvider> providers = new ConcurrentHashMap<>();
    private String currentProviderId = "openai";

    public ProviderStore() {
        // Pre-seed default providers
        providers.put("openai", AiProvider.builder()
            .id("openai")
            .name("OpenAI")
            .baseUrl("https://api.openai.com")
            .apiKey("")
            .apiFormat("openai")
            .models(List.of(
                ModelInfo.builder().id("gpt-4o").name("GPT-4o").build(),
                ModelInfo.builder().id("gpt-4o-mini").name("GPT-4o Mini").build(),
                ModelInfo.builder().id("gpt-4-turbo").name("GPT-4 Turbo").build(),
                ModelInfo.builder().id("gpt-3.5-turbo").name("GPT-3.5 Turbo").build()
            ))
            .currentModel("gpt-4o-mini")
            .enabled(true)
            .build());

        providers.put("anthropic", AiProvider.builder()
            .id("anthropic")
            .name("Anthropic")
            .baseUrl("https://api.anthropic.com")
            .apiKey("")
            .apiFormat("anthropic")
            .models(List.of(
                ModelInfo.builder().id("claude-sonnet-4-20250514").name("Claude Sonnet 4").build(),
                ModelInfo.builder().id("claude-opus-4-20250514").name("Claude Opus 4").build(),
                ModelInfo.builder().id("claude-haiku-4-20250514").name("Claude Haiku 4").build()
            ))
            .currentModel("claude-sonnet-4-20250514")
            .enabled(true)
            .build());
    }

    public List<AiProvider> listAll() {
        return new ArrayList<>(providers.values());
    }

    public AiProvider get(String id) {
        return providers.get(id);
    }

    public void addOrUpdate(AiProvider provider) {
        if (provider.getId() == null || provider.getId().isEmpty()) {
            provider.setId(java.util.UUID.randomUUID().toString().substring(0, 8));
        }
        providers.put(provider.getId(), provider);
    }

    public boolean delete(String id) {
        if (providers.size() <= 1) {
            return false; // Keep at least one provider
        }
        boolean removed = providers.remove(id) != null;
        if (removed && id.equals(currentProviderId)) {
            currentProviderId = providers.keySet().iterator().next();
        }
        return removed;
    }

    public String getCurrentProviderId() {
        return currentProviderId;
    }

    public void setCurrentProviderId(String id) {
        if (providers.containsKey(id)) {
            this.currentProviderId = id;
        }
    }

    public AiProvider getCurrentProvider() {
        AiProvider p = providers.get(currentProviderId);
        if (p == null && !providers.isEmpty()) {
            p = providers.values().iterator().next();
            currentProviderId = p.getId();
        }
        return p;
    }

    public ProviderConfig toCurrentProviderConfig() {
        AiProvider p = getCurrentProvider();
        return p != null ? p.toProviderConfig() : ProviderConfig.builder().build();
    }

    // Backward compatibility for old ConfigStore API
    public String getConfigValue(String key) {
        AiProvider p = getCurrentProvider();
        if (p == null) return "";
        return switch (key) {
            case "model" -> p.getCurrentModel();
            case "baseUrl" -> p.getBaseUrl();
            case "apiKey" -> p.getApiKey();
            default -> "";
        };
    }

    public void setConfigValue(String key, String value) {
        AiProvider p = getCurrentProvider();
        if (p == null) return;
        switch (key) {
            case "model" -> p.setCurrentModel(value);
            case "baseUrl" -> p.setBaseUrl(value);
            case "apiKey" -> p.setApiKey(value);
        }
    }
}
