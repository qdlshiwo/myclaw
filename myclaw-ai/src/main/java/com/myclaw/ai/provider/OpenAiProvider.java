package com.myclaw.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.config.ProviderConfig;
import com.myclaw.core.protocol.ChatMessage;
import com.myclaw.core.tool.ToolCall;
import com.myclaw.core.tool.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
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
    public Flux<StreamChunk> streamChat(String model, List<ChatMessage> messages, String systemPrompt, ProviderConfig config, List<ToolDefinition> tools) {
        String apiKey = config != null && config.getApiKey() != null ? config.getApiKey() : "";
        String baseUrl = config != null && config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.openai.com";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model != null ? model : "gpt-4o-mini");
        body.put("stream", true);

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArr = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode t = toolsArr.addObject();
                t.put("type", "function");
                ObjectNode fn = t.putObject("function");
                fn.put("name", tool.getName());
                fn.put("description", tool.getDescription());
                fn.set("parameters", tool.getParameters());
            }
        }

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
            if (msg.getToolCalls() != null) {
                try {
                    m.set("tool_calls", objectMapper.readTree(msg.getToolCalls()));
                } catch (Exception e) {
                    log.warn("Failed to parse tool_calls JSON: {}", msg.getToolCalls());
                }
            }
            if (msg.getToolCallId() != null) {
                m.put("tool_call_id", msg.getToolCallId());
            }
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

    @Override
    public ChatCompletion chatComplete(String model, List<ChatMessage> messages, String systemPrompt, ProviderConfig config, List<ToolDefinition> tools) {
        String apiKey = config != null && config.getApiKey() != null ? config.getApiKey() : "";
        String baseUrl = config != null && config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.openai.com";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model != null ? model : "gpt-4o-mini");
        body.put("stream", false);

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArr = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode t = toolsArr.addObject();
                t.put("type", "function");
                ObjectNode fn = t.putObject("function");
                fn.put("name", tool.getName());
                fn.put("description", tool.getDescription());
                fn.set("parameters", tool.getParameters());
            }
        }

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
            if (msg.getToolCalls() != null) {
                try {
                    m.set("tool_calls", objectMapper.readTree(msg.getToolCalls()));
                } catch (Exception e) {
                    log.warn("Failed to parse tool_calls JSON: {}", msg.getToolCalls());
                }
            }
            if (msg.getToolCallId() != null) {
                m.put("tool_call_id", msg.getToolCallId());
            }
        }

        String uri = buildChatUri(baseUrl);

        try {
            String responseJson = webClient.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return ChatCompletion.builder().content("").build();
            }
            JsonNode message = choices.get(0).path("message");
            String content = message.path("content").asText(null);

            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode tcArr = message.path("tool_calls");
            if (tcArr.isArray()) {
                for (JsonNode tc : tcArr) {
                    if ("function".equals(tc.path("type").asText(""))) {
                        toolCalls.add(ToolCall.builder()
                            .id(tc.path("id").asText(""))
                            .name(tc.path("function").path("name").asText(""))
                            .arguments(tc.path("function").path("arguments").asText("{}"))
                            .build());
                    }
                }
            }

            return ChatCompletion.builder()
                .content(content)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .build();
        } catch (Exception e) {
            log.error("OpenAI chat complete error", e);
            return ChatCompletion.builder()
                .content("")
                .toolCalls(null)
                .build();
        }
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
