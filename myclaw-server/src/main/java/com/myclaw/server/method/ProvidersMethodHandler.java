package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
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
public class ProvidersMethodHandler implements GatewayMethodHandler {

    private final ProviderStore providerStore;
    private final ObjectMapper objectMapper;

    public ProvidersMethodHandler(ProviderStore providerStore, ObjectMapper objectMapper) {
        this.providerStore = providerStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getMethod() {
        return "providers";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        JsonNode params = request.getParams();
        String action = params.path("action").asText("list");

        switch (action) {
            case "list" -> {
                List<AiProvider> providers = providerStore.listAll();
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("current", providerStore.getCurrentProviderId());
                ArrayNode arr = payload.putArray("providers");
                for (AiProvider p : providers) {
                    arr.add(toJson(p));
                }
                return ok(request, payload);
            }
            case "get" -> {
                String id = params.path("id").asText("");
                AiProvider p = providerStore.get(id);
                if (p == null) {
                    return error(request, "not_found", "Provider not found: " + id);
                }
                return ok(request, toJson(p));
            }
            case "add", "update" -> {
                AiProvider provider = objectMapper.convertValue(params.path("provider"), AiProvider.class);
                providerStore.addOrUpdate(provider);
                return ok(request, toJson(provider));
            }
            case "delete" -> {
                String id = params.path("id").asText("");
                boolean deleted = providerStore.delete(id);
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("deleted", deleted);
                return ok(request, payload);
            }
            case "switch" -> {
                String id = params.path("id").asText("");
                providerStore.setCurrentProviderId(id);
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("current", providerStore.getCurrentProviderId());
                AiProvider p = providerStore.getCurrentProvider();
                if (p != null) {
                    payload.set("provider", toJson(p));
                }
                return ok(request, payload);
            }
            default -> {
                return error(request, "unsupported_action", "Unsupported action: " + action);
            }
        }
    }

    private ObjectNode toJson(AiProvider p) {
        ObjectNode obj = objectMapper.createObjectNode();
        obj.put("id", p.getId());
        obj.put("name", p.getName());
        obj.put("baseUrl", p.getBaseUrl());
        obj.put("apiKey", p.getApiKey());
        obj.put("apiFormat", p.getApiFormat());
        obj.put("currentModel", p.getCurrentModel());
        obj.put("enabled", p.isEnabled());
        ArrayNode models = obj.putArray("models");
        if (p.getModels() != null) {
            for (ModelInfo m : p.getModels()) {
                ObjectNode mo = models.addObject();
                mo.put("id", m.getId());
                mo.put("name", m.getName());
            }
        }
        return obj;
    }

    private Mono<GatewayFrame> ok(GatewayFrame req, ObjectNode payload) {
        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(req.getId())
            .ok(true)
            .payload(payload)
            .build());
    }

    private Mono<GatewayFrame> error(GatewayFrame req, String code, String message) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("errorCode", code);
        payload.put("errorMessage", message);
        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(req.getId())
            .ok(false)
            .payload(payload)
            .build());
    }
}
