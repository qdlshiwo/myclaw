package com.myclaw.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.config.ProviderConfig;
import com.myclaw.core.protocol.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OpenAiProvider implements ModelProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderId() {
        return "openai";
    }

    private String buildChatUri(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        // If URL already contains chat completions path, use as-is
        if (baseUrl.contains("/chat/completions") || baseUrl.contains("/chatcompletion")) {
            return baseUrl;
        }
        // If URL ends with version segment like /v1, /v4, append /chat/completions
        if (baseUrl.matches(".*/v\\d+$") || baseUrl.matches(".*/v\\d+\\.\\d+$")) {
            return baseUrl + "/chat/completions";
        }
        // Default: append /v1/chat/completions
        return baseUrl + "/v1/chat/completions";
    }

    @Override
    public Flux<StreamChunk> streamChat(String model, List<ChatMessage> messages, String systemPrompt, ProviderConfig config) {
        String apiKey = config != null && config.getApiKey() != null ? config.getApiKey() : "";
        String baseUrl = config != null && config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.openai.com";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model != null ? model : "gpt-4o-mini");
        body.put("stream", true);

        ArrayNode msgs = body.putArray("messages");
        if (systemPrompt != null) {
            ObjectNode sys = msgs.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        for (ChatMessage msg : messages) {
            ObjectNode m = msgs.addObject();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
        }

        String uri = buildChatUri(baseUrl);

        return webClient.post()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class)
            .flatMap(line -> parseStreamLine(line))
            .onErrorResume(e -> {
                log.error("OpenAI stream error", e);
                return Flux.just(StreamChunk.builder()
                    .type(StreamChunk.Type.ERROR)
                    .errorMessage(e.getMessage())
                    .build());
            });
    }

    private Flux<StreamChunk> parseStreamLine(String line) {
        if (!line.startsWith("data: ")) {
            return Flux.empty();
        }
        String data = line.substring(6);
        if ("[DONE]".equals(data)) {
            return Flux.just(StreamChunk.builder().type(StreamChunk.Type.FINISH).build());
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return Flux.empty();
            }
            JsonNode delta = choices.get(0).path("delta");
            String content = delta.path("content").asText(null);
            if (content != null) {
                return Flux.just(StreamChunk.builder()
                    .type(StreamChunk.Type.CONTENT)
                    .content(content)
                    .build());
            }
            String finish = choices.get(0).path("finish_reason").asText(null);
            if (finish != null && !finish.isEmpty() && !"null".equals(finish)) {
                return Flux.just(StreamChunk.builder()
                    .type(StreamChunk.Type.FINISH)
                    .finishReason(finish)
                    .build());
            }
            return Flux.empty();
        } catch (Exception e) {
            log.warn("Failed to parse SSE line: {}", line);
            return Flux.empty();
        }
    }
}
