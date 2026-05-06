package com.myclaw.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.config.ProviderConfig;
import com.myclaw.core.protocol.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
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
        log.info("Anthropic request uri={}, model={}", uri, model);

        return webClient.post()
            .uri(uri)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class)
            .doOnNext(line -> log.info("Anthropic SSE raw: {}", line))
            .flatMap(line -> parseStreamLine(line))
            .timeout(Duration.ofSeconds(60))
            .onErrorResume(e -> {
                log.error("Anthropic stream error: {}", e.getMessage(), e);
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
        if (line == null || line.isEmpty()) {
            return Flux.empty();
        }
        // Skip event type lines in SSE
        if (line.startsWith("event: ")) {
            return Flux.empty();
        }

        String data = line;
        if (line.startsWith("data: ")) {
            data = line.substring(6);
        }
        if ("[DONE]".equals(data)) {
            return Flux.just(StreamChunk.builder().type(StreamChunk.Type.FINISH).build());
        }
        try {
            JsonNode root = objectMapper.readTree(data);

            // Try Anthropic native format first
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

            // Fallback 1: OpenAI-compatible streaming format
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
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
            }

            // Fallback 2: non-streaming Anthropic response (single JSON object)
            JsonNode contentArr = root.path("content");
            if (contentArr.isArray() && !contentArr.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : contentArr) {
                    String text = block.path("text").asText(null);
                    if (text != null) sb.append(text);
                }
                if (sb.length() > 0) {
                    return Flux.just(
                        StreamChunk.builder().type(StreamChunk.Type.CONTENT).content(sb.toString()).build(),
                        StreamChunk.builder().type(StreamChunk.Type.FINISH).build()
                    );
                }
            }

            // Fallback 3: non-streaming OpenAI response
            JsonNode message = root.path("choices").get(0).path("message");
            if (!message.isMissingNode()) {
                String content = message.path("content").asText(null);
                if (content != null) {
                    return Flux.just(
                        StreamChunk.builder().type(StreamChunk.Type.CONTENT).content(content).build(),
                        StreamChunk.builder().type(StreamChunk.Type.FINISH).build()
                    );
                }
            }

            return Flux.empty();
        } catch (Exception e) {
            log.warn("Failed to parse SSE line: {}", line);
            return Flux.empty();
        }
    }
}
