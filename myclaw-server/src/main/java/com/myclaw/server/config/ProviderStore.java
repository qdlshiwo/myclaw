package com.myclaw.server.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myclaw.core.config.AiProvider;
import com.myclaw.core.config.ModelInfo;
import com.myclaw.core.config.ProviderConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ProviderStore {

    private final Map<String, AiProvider> providers = new ConcurrentHashMap<>();
    private String currentProviderId = "openai";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path storePath;

    public ProviderStore() {
        String userHome = System.getProperty("user.home");
        this.storePath = Path.of(userHome, ".qoderwork", "myclaw", "providers.json");
    }

    @PostConstruct
    public void init() {
        if (Files.exists(storePath)) {
            try {
                String json = Files.readString(storePath);
                StoreData data = objectMapper.readValue(json, StoreData.class);
                if (data.providers != null) {
                    providers.clear();
                    for (AiProvider p : data.providers) {
                        if (p.getModelMappings() == null) {
                            p.setModelMappings(new LinkedHashMap<>());
                        }
                        providers.put(p.getId(), p);
                    }
                }
                if (data.currentProviderId != null && providers.containsKey(data.currentProviderId)) {
                    currentProviderId = data.currentProviderId;
                }
                log.info("Loaded {} providers from {}", providers.size(), storePath);
                return;
            } catch (IOException e) {
                log.warn("Failed to load providers from {}, using defaults", storePath, e);
            }
        }
        seedDefaults();
    }

    private void seedDefaults() {
        providers.put("openai", AiProvider.builder()
            .id("openai")
            .name("OpenAI")
            .websiteUrl("https://openai.com")
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
            .modelMappings(new LinkedHashMap<>())
            .build());

        providers.put("anthropic", AiProvider.builder()
            .id("anthropic")
            .name("Anthropic")
            .websiteUrl("https://www.anthropic.com")
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
            .modelMappings(new LinkedHashMap<>())
            .build());

        // Chinese model providers (Anthropic-compatible endpoints)
        providers.put("deepseek", AiProvider.builder()
            .id("deepseek")
            .name("DeepSeek")
            .websiteUrl("https://platform.deepseek.com")
            .baseUrl("https://api.deepseek.com/anthropic")
            .apiKey("")
            .apiFormat("anthropic")
            .models(List.of(
                ModelInfo.builder().id("deepseek-v4-pro").name("DeepSeek V4 Pro").build(),
                ModelInfo.builder().id("deepseek-v4-flash").name("DeepSeek V4 Flash").build()
            ))
            .currentModel("deepseek-v4-pro")
            .enabled(true)
            .modelMappings(new LinkedHashMap<>())
            .build());

        providers.put("glm", AiProvider.builder()
            .id("glm")
            .name("智谱 GLM")
            .websiteUrl("https://open.bigmodel.cn")
            .baseUrl("https://open.bigmodel.cn/api/anthropic")
            .apiKey("")
            .apiFormat("anthropic")
            .models(List.of(
                ModelInfo.builder().id("glm-5").name("GLM-5").build(),
                ModelInfo.builder().id("glm-4.7").name("GLM-4.7").build()
            ))
            .currentModel("glm-5")
            .enabled(true)
            .modelMappings(new LinkedHashMap<>())
            .build());

        providers.put("kimi", AiProvider.builder()
            .id("kimi")
            .name("Kimi (Moonshot)")
            .websiteUrl("https://platform.moonshot.cn")
            .baseUrl("https://api.moonshot.cn/anthropic")
            .apiKey("")
            .apiFormat("anthropic")
            .models(List.of(
                ModelInfo.builder().id("kimi-k2.6").name("Kimi K2.6").build(),
                ModelInfo.builder().id("kimi-k2.5").name("Kimi K2.5").build(),
                ModelInfo.builder().id("kimi-k2").name("Kimi K2").build()
            ))
            .currentModel("kimi-k2.6")
            .enabled(true)
            .modelMappings(new LinkedHashMap<>())
            .build());

        providers.put("bailian", AiProvider.builder()
            .id("bailian")
            .name("阿里百炼 (Bailian)")
            .websiteUrl("https://bailian.console.aliyun.com")
            .baseUrl("https://dashscope.aliyuncs.com/apps/anthropic")
            .apiKey("")
            .apiFormat("anthropic")
            .models(List.of(
                ModelInfo.builder().id("qwen3.6-plus").name("Qwen3.6 Plus").build(),
                ModelInfo.builder().id("qwen3.5-plus").name("Qwen3.5 Plus").build(),
                ModelInfo.builder().id("qwen3-max").name("Qwen3 Max").build(),
                ModelInfo.builder().id("qwen3.6-coder-plus").name("Qwen3.6 Coder Plus").build()
            ))
            .currentModel("qwen3.6-plus")
            .enabled(true)
            .modelMappings(new LinkedHashMap<>())
            .build());

        providers.put("minimax", AiProvider.builder()
            .id("minimax")
            .name("MiniMax")
            .websiteUrl("https://platform.minimaxi.com")
            .baseUrl("https://api.minimaxi.com/anthropic")
            .apiKey("")
            .apiFormat("anthropic")
            .models(List.of(
                ModelInfo.builder().id("MiniMax-M2.7").name("MiniMax M2.7").build(),
                ModelInfo.builder().id("MiniMax-M2.7-highspeed").name("MiniMax M2.7 Highspeed").build(),
                ModelInfo.builder().id("MiniMax-M2.5").name("MiniMax M2.5").build(),
                ModelInfo.builder().id("MiniMax-M2.5-highspeed").name("MiniMax M2.5 Highspeed").build()
            ))
            .currentModel("MiniMax-M2.7")
            .enabled(true)
            .modelMappings(new LinkedHashMap<>())
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
        if (provider.getModelMappings() == null) {
            provider.setModelMappings(new LinkedHashMap<>());
        }
        providers.put(provider.getId(), provider);
        save();
    }

    public boolean delete(String id) {
        if (providers.size() <= 1) {
            return false; // Keep at least one provider
        }
        boolean removed = providers.remove(id) != null;
        if (removed && id.equals(currentProviderId)) {
            currentProviderId = providers.keySet().iterator().next();
        }
        if (removed) save();
        return removed;
    }

    public String getCurrentProviderId() {
        return currentProviderId;
    }

    public void setCurrentProviderId(String id) {
        if (providers.containsKey(id)) {
            this.currentProviderId = id;
            save();
        }
    }

    private void save() {
        try {
            Files.createDirectories(storePath.getParent());
            StoreData data = new StoreData();
            data.currentProviderId = currentProviderId;
            data.providers = new ArrayList<>(providers.values());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), data);
        } catch (IOException e) {
            log.warn("Failed to save providers to {}", storePath, e);
        }
    }

    public static class StoreData {
        public String currentProviderId;
        public List<AiProvider> providers;
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
