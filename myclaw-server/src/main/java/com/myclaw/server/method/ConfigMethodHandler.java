package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.server.config.ConfigStore;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class ConfigMethodHandler implements GatewayMethodHandler {

    private final ConfigStore configStore;
    private final ObjectMapper objectMapper;

    public ConfigMethodHandler(ConfigStore configStore, ObjectMapper objectMapper) {
        this.configStore = configStore;
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
                    configStore.set(entry.getKey(), entry.getValue().asText(""));
                });
            }
        }

        Map<String, String> all = configStore.getAll();
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode configObj = payload.putObject("config");
        all.forEach(configObj::put);

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }
}
