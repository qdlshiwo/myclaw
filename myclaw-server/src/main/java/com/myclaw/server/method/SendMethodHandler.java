package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.model.Session;
import com.myclaw.core.protocol.ChatMessage;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.server.session.InMemorySessionManager;
import com.myclaw.server.websocket.GatewayEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class SendMethodHandler implements GatewayMethodHandler {

    private final InMemorySessionManager sessionManager;
    private final GatewayEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public SendMethodHandler(InMemorySessionManager sessionManager,
                             GatewayEventPublisher eventPublisher,
                             ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getMethod() {
        return "send";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        JsonNode params = request.getParams();
        String sessionKey = params.path("sessionKey").asText("main");
        String content = params.path("content").asText("");
        String agentId = "main";

        Session sess = sessionManager.getOrCreate(sessionKey, agentId);

        ChatMessage msg = ChatMessage.builder()
            .role("user")
            .content(content)
            .timestamp(Instant.now().toString())
            .build();
        sess.getMessages().add(msg);
        sess.setLastInteractionAt(Instant.now());

        // Broadcast chat event to all connected clients
        ObjectNode eventPayload = objectMapper.createObjectNode();
        eventPayload.put("sessionKey", sessionKey);
        eventPayload.put("sessionId", sess.getSessionId());
        eventPayload.put("role", "user");
        eventPayload.put("content", content);
        eventPayload.put("timestamp", msg.getTimestamp());
        eventPublisher.broadcastEvent("chat", eventPayload);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sent", true);
        payload.put("sessionId", sess.getSessionId());

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }
}
