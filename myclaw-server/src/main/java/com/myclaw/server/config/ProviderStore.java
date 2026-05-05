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

        // Chinese model providers
        providers.put("deepseek", AiProvider.builder()
            .id("deepseek")
            .name("DeepSeek")
            .baseUrl("https://api.deepseek.com")
            .apiKey("")
            .apiFormat("openai")
            .models(List.of(
                ModelInfo.builder().id("deepseek-chat").name("DeepSeek V3").build(),
                ModelInfo.builder().id("deepseek-reasoner").name("DeepSeek R1").build()
            ))
            .currentModel("deepseek-chat")
            .enabled(true)
            .build());

        providers.put("glm", AiProvider.builder()
            .id("glm")
            .name("智谱 GLM")
            .baseUrl("https://open.bigmodel.cn/api/paas/v4")
            .apiKey("")
            .apiFormat("openai")
            .models(List.of(
                ModelInfo.builder().id("glm-4").name("GLM-4").build(),
                ModelInfo.builder().id("glm-4-plus").name("GLM-4 Plus").build(),
                ModelInfo.builder().id("glm-4-air").name("GLM-4 Air").build(),
                ModelInfo.builder().id("glm-4-flash").name("GLM-4 Flash").build()
            ))
            .currentModel("glm-4")
            .enabled(true)
            .build());

        providers.put("kimi", AiProvider.builder()
            .id("kimi")
            .name("Kimi (Moonshot)")
            .baseUrl("https://api.moonshot.cn")
            .apiKey("")
            .apiFormat("openai")
            .models(List.of(
                ModelInfo.builder().id("moonshot-v1-8k").name("Moonshot v1 8K").build(),
                ModelInfo.builder().id("moonshot-v1-32k").name("Moonshot v1 32K").build(),
                ModelInfo.builder().id("moonshot-v1-128k").name("Moonshot v1 128K").build()
            ))
            .currentModel("moonshot-v1-8k")
            .enabled(true)
            .build());

        providers.put("bailian", AiProvider.builder()
            .id("bailian")
            .name("阿里百炼 (Bailian)")
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
            .apiKey("")
            .apiFormat("openai")
            .models(List.of(
                ModelInfo.builder().id("qwen-max").name("Qwen Max").build(),
                ModelInfo.builder().id("qwen-plus").name("Qwen Plus").build(),
                ModelInfo.builder().id("qwen-turbo").name("Qwen Turbo").build(),
                ModelInfo.builder().id("qwen-coder-plus").name("Qwen Coder Plus").build()
            ))
            .currentModel("qwen-plus")
            .enabled(true)
            .build());

        providers.put("minimax", AiProvider.builder()
            .id("minimax")
            .name("MiniMax")
            .baseUrl("https://api.minimax.chat")
            .apiKey("")
            .apiFormat("openai")
            .models(List.of(
                ModelInfo.builder().id("abab6.5").name("abab6.5").build(),
                ModelInfo.builder().id("abab6.5s").name("abab6.5s").build(),
                ModelInfo.builder().id("abab5.5").name("abab5.5").build()
            ))
            .currentModel("abab6.5")
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
