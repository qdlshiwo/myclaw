package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.model.Session;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.server.session.InMemorySessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class SessionsMethodHandler implements GatewayMethodHandler {

    private final InMemorySessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public SessionsMethodHandler(InMemorySessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getMethod() {
        return "sessions";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        JsonNode params = request.getParams();
        String action = params.path("action").asText("list");

        if ("delete".equals(action)) {
            return handleDelete(session, request, params);
        }

        // Default: list with pagination
        int page = params.path("page").asInt(0);
        int pageSize = params.path("pageSize").asInt(10);
        String search = params.path("search").asText(null);

        List<Session> pagedSessions = sessionManager.listPaged(page, pageSize, search);
        long total = sessionManager.countAll(search);

        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode arr = payload.putArray("sessions");
        for (Session s : pagedSessions) {
            ObjectNode obj = arr.addObject();
            obj.put("sessionId", s.getSessionId());
            obj.put("sessionKey", s.getSessionKey());
            obj.put("agentId", s.getAgentId());
            obj.put("messageCount", s.getMessages().size());
            obj.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
            obj.put("lastInteractionAt", s.getLastInteractionAt() != null ? s.getLastInteractionAt().toString() : null);
        }
        payload.put("page", page);
        payload.put("pageSize", pageSize);
        payload.put("total", total);
        payload.put("totalPages", (int) Math.ceil((double) total / pageSize));

        String lastSessionKey = total == 0 ? null : pagedSessions.isEmpty() ? null :
            sessionManager.listPaged(0, 1, null).get(0).getSessionKey();
        payload.put("lastSessionKey", lastSessionKey);

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }

    private Mono<GatewayFrame> handleDelete(WebSocketSession session, GatewayFrame request, JsonNode params) {
        JsonNode keysNode = params.path("sessionKeys");
        if (keysNode.isArray() && keysNode.size() > 0) {
            java.util.List<String> keys = new java.util.ArrayList<>();
            keysNode.forEach(n -> keys.add(n.asText()));
            int deleted = sessionManager.deleteAll(keys);
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("deleted", deleted);
            return Mono.just(GatewayFrame.builder()
                .type("res")
                .id(request.getId())
                .ok(true)
                .payload(payload)
                .build());
        } else if (keysNode.isTextual()) {
            boolean ok = sessionManager.delete(keysNode.asText());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("deleted", ok ? 1 : 0);
            return Mono.just(GatewayFrame.builder()
                .type("res")
                .id(request.getId())
                .ok(true)
                .payload(payload)
                .build());
        }

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(false)
            .error(com.myclaw.core.protocol.GatewayError.of("invalid_params", "sessionKeys required"))
            .build());
    }
}
