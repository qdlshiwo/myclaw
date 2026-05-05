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
public class AnthropicProvider implements ModelProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderId() {
        return "anthropic";
    }

    @Override
    public Flux<StreamChunk> streamChat(String model, List<ChatMessage> messages, String systemPrompt, ProviderConfig config) {
        String apiKey = config != null && config.getApiKey() != null ? config.getApiKey() : "";
        String baseUrl = config != null && config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.anthropic.com";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model != null ? model : "claude-sonnet-4-20250514");
        body.put("max_tokens", 4096);
        body.put("stream", true);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            body.put("system", systemPrompt);
        }

        ArrayNode msgs = body.putArray("messages");
        for (ChatMessage msg : messages) {
            // Anthropic only supports user/assistant roles in messages array
            String role = msg.getRole();
            if ("system".equals(role)) continue;
            ObjectNode m = msgs.addObject();
            m.put("role", role);
            m.put("content", msg.getContent());
        }

        // Ensure at least one message
        if (msgs.isEmpty()) {
            ObjectNode m = msgs.addObject();
            m.put("role", "user");
            m.put("content", "Hello");
        }

        String uri = buildMessagesUri(baseUrl);

        return webClient.post()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class)
            .flatMap(line -> parseStreamLine(line))
            .onErrorResume(e -> {
                log.error("Anthropic stream error", e);
                return Flux.just(StreamChunk.builder()
                    .type(StreamChunk.Type.ERROR)
                    .errorMessage(e.getMessage())
                    .build());
            });
    }

    private String buildMessagesUri(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.anthropic.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.contains("/v1/messages")) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/v1")) {
            return baseUrl + "/messages";
        }
        if (baseUrl.endsWith("/anthropic")) {
            // Provider-specific anthropic-compatible endpoint
            return baseUrl + "/v1/messages";
        }
        return baseUrl + "/v1/messages";
    }

    private Flux<StreamChunk> parseStreamLine(String line) {
        if (line.startsWith("event: ")) {
            // Store event type for next data line; for now skip
            return Flux.empty();
        }
        if (!line.startsWith("data: ")) {
            return Flux.empty();
        }
        String data = line.substring(6);
        if ("[DONE]".equals(data)) {
            return Flux.just(StreamChunk.builder().type(StreamChunk.Type.FINISH).build());
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            String type = root.path("type").asText("");

            if ("content_block_delta".equals(type)) {
                JsonNode delta = root.path("delta");
                String text = delta.path("text").asText(null);
                if (text != null) {
                    return Flux.just(StreamChunk.builder()
                        .type(StreamChunk.Type.CONTENT)
                        .content(text)
                        .build());
                }
            } else if ("message_delta".equals(type)) {
                JsonNode delta = root.path("delta");
                String stopReason = delta.path("stop_reason").asText(null);
                if (stopReason != null && !stopReason.isEmpty()) {
                    return Flux.just(StreamChunk.builder()
                        .type(StreamChunk.Type.FINISH)
                        .finishReason(stopReason)
                        .build());
                }
            } else if ("message_stop".equals(type)) {
                return Flux.just(StreamChunk.builder().type(StreamChunk.Type.FINISH).build());
            }
            return Flux.empty();
        } catch (Exception e) {
            log.warn("Failed to parse Anthropic SSE line: {}", line);
            return Flux.empty();
        }
    }
}
