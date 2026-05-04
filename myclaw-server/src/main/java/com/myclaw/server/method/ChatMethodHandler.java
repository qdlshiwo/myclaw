package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.model.Session;
import com.myclaw.core.protocol.ChatMessage;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.server.session.InMemorySessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class ChatMethodHandler implements GatewayMethodHandler {

    private final InMemorySessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public ChatMethodHandler(InMemorySessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getMethod() {
        return "chat";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        JsonNode params = request.getParams();
        String sessionKey = params.path("sessionKey").asText("main");
        String agentId = "main";

        Session sess = sessionManager.getOrCreate(sessionKey, agentId);
        List<ChatMessage> messages = sess.getMessages();

        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode arr = payload.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode m = arr.addObject();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            m.put("timestamp", msg.getTimestamp());
            m.put("runId", msg.getRunId());
        }
        payload.put("sessionId", sess.getSessionId());
        payload.put("sessionKey", sess.getSessionKey());

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }
}
