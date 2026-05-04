package com.myclaw.server.method;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.model.Session;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.server.session.InMemorySessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Collection;

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
        Collection<Session> sessions = sessionManager.listAll();

        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode arr = payload.putArray("sessions");
        for (Session s : sessions) {
            ObjectNode obj = arr.addObject();
            obj.put("sessionId", s.getSessionId());
            obj.put("sessionKey", s.getSessionKey());
            obj.put("agentId", s.getAgentId());
            obj.put("messageCount", s.getMessages().size());
            obj.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
            obj.put("lastInteractionAt", s.getLastInteractionAt() != null ? s.getLastInteractionAt().toString() : null);
        }

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }
}
