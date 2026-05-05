package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.core.config.AiProvider;
import com.myclaw.server.config.ProviderStore;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class ConfigMethodHandler implements GatewayMethodHandler {

    private final ProviderStore providerStore;
    private final ObjectMapper objectMapper;

    public ConfigMethodHandler(ProviderStore providerStore, ObjectMapper objectMapper) {
        this.providerStore = providerStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getMethod() {
        return "config";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        JsonNode params = request.getParams();
        String action = params.path("action").asText("get");

        if ("set".equals(action)) {
            JsonNode configNode = params.path("config");
            if (configNode.isObject()) {
                configNode.fields().forEachRemaining(entry -> {
                    providerStore.setConfigValue(entry.getKey(), entry.getValue().asText(""));
                });
            }
        }

        AiProvider current = providerStore.getCurrentProvider();
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode configObj = payload.putObject("config");
        if (current != null) {
            configObj.put("model", current.getCurrentModel());
            configObj.put("baseUrl", current.getBaseUrl());
            configObj.put("apiKey", current.getApiKey());
            configObj.put("provider", current.getId());
            configObj.put("providerName", current.getName());
        }

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }
}
