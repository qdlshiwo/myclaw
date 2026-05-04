package com.myclaw.ai.provider;

import com.myclaw.core.config.ProviderConfig;
import com.myclaw.core.protocol.ChatMessage;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ModelProvider {

    String getProviderId();

    Flux<StreamChunk> streamChat(String model, List<ChatMessage> messages, String systemPrompt, ProviderConfig config);
}
