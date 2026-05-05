package com.myclaw.server.method;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.config.AiProvider;
import com.myclaw.core.config.ModelInfo;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.server.config.ProviderStore;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class ModelsMethodHandler implements GatewayMethodHandler {

    private final ProviderStore providerStore;
    private final ObjectMapper objectMapper;

    public ModelsMethodHandler(ProviderStore providerStore, ObjectMapper objectMapper) {
        this.providerStore = providerStore;
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

        AiProvider current = providerStore.getCurrentProvider();
        if (current != null && current.getModels() != null) {
            for (ModelInfo m : current.getModels()) {
                addModel(models, m.getId(), current.getName(), m.getName());
            }
        }

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
