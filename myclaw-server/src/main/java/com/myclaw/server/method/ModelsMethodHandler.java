package com.myclaw.server.method;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.protocol.GatewayFrame;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class ModelsMethodHandler implements GatewayMethodHandler {

    private final ObjectMapper objectMapper;

    public ModelsMethodHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getMethod() {
        return "models";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode models = payload.putArray("models");

        addModel(models, "openai/gpt-4o", "OpenAI", "GPT-4o");
        addModel(models, "openai/gpt-4o-mini", "OpenAI", "GPT-4o Mini");
        addModel(models, "openai/gpt-4-turbo", "OpenAI", "GPT-4 Turbo");
        addModel(models, "openai/gpt-3.5-turbo", "OpenAI", "GPT-3.5 Turbo");

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }

    private void addModel(ArrayNode arr, String id, String provider, String name) {
        ObjectNode obj = arr.addObject();
        obj.put("id", id);
        obj.put("provider", provider);
        obj.put("name", name);
    }
}
