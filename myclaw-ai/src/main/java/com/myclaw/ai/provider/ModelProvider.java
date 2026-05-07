package com.myclaw.ai.provider;

import com.myclaw.core.config.ProviderConfig;
import com.myclaw.core.protocol.ChatMessage;
import com.myclaw.core.tool.ToolDefinition;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

public interface ModelProvider {

    String getProviderId();

    default Flux<StreamChunk> streamChat(String model, List<ChatMessage> messages, String systemPrompt, ProviderConfig config) {
        return streamChat(model, messages, systemPrompt, config, Collections.emptyList());
    }

    Flux<StreamChunk> streamChat(String model, List<ChatMessage> messages, String systemPrompt, ProviderConfig config, List<ToolDefinition> tools);

    ChatCompletion chatComplete(String model, List<ChatMessage> messages, String systemPrompt, ProviderConfig config, List<ToolDefinition> tools);
}
