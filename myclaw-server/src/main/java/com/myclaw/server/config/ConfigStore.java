package com.myclaw.server.config;

import com.myclaw.core.config.ProviderConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConfigStore {

    private final Map<String, String> values = new ConcurrentHashMap<>();

    public ConfigStore() {
        // Defaults
        values.put("model", "gpt-4o-mini");
        values.put("baseUrl", "https://api.openai.com");
        values.put("apiKey", "");
    }

    public String get(String key) {
        return values.get(key);
    }

    public void set(String key, String value) {
        values.put(key, value);
    }

    public Map<String, String> getAll() {
        return new ConcurrentHashMap<>(values);
    }

    public ProviderConfig toProviderConfig() {
        return ProviderConfig.builder()
            .apiKey(values.get("apiKey"))
            .baseUrl(values.get("baseUrl"))
            .model(values.get("model"))
            .build();
    }
}
