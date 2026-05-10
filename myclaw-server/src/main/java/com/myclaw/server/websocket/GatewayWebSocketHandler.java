package com.myclaw.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myclaw.core.protocol.GatewayError;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.core.util.JsonUtils;
import com.myclaw.server.method.GatewayMethodRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class GatewayWebSocketHandler extends TextWebSocketHandler implements GatewayEventPublisher {

    private final ObjectMapper objectMapper;
    private final GatewayMethodRegistry methodRegistry;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public GatewayWebSocketHandler(ObjectMapper objectMapper, @Lazy GatewayMethodRegistry methodRegistry) {
        this.objectMapper = objectMapper;
        this.methodRegistry = methodRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WS connection established: {}", session.getId());
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            GatewayFrame frame = objectMapper.readValue(payload, GatewayFrame.class);
            if (!frame.isRequest()) {
                sendError(session, null, "invalid_frame", "Expected req frame");
                return;
            }
            // Log user request (method + key fields)
            String method = frame.getMethod();
            JsonNode params = frame.getParams();
            if (params != null) {
                String content = params.path("content").asText(null);
                String msg = params.path("message").asText(null);
                String sessionKey = params.path("sessionKey").asText(null);
                String logContent = content != null ? content : (msg != null ? msg : "(no content)");
                log.info("WS request: method={}, sessionKey={}, content={}", method, sessionKey, logContent);
            } else {
                log.info("WS request: method={}", method);
            }
            handleRequest(session, frame);
        } catch (Exception e) {
            log.error("Failed to handle message: {}", payload, e);
            try {
                GatewayFrame parsed = objectMapper.readValue(payload, GatewayFrame.class);
                sendError(session, parsed.getId(), "parse_error", e.getMessage());
            } catch (Exception ex) {
                sendError(session, null, "parse_error", "Invalid JSON");
            }
        }
    }

    private void handleRequest(WebSocketSession session, GatewayFrame req) {
        var handler = methodRegistry.getHandler(req.getMethod());
        if (handler == null) {
            sendError(session, req.getId(), "method_not_found", "Method not found: " + req.getMethod());
            return;
        }
        try {
            handler.handle(session, req).subscribe(
                res -> sendFrame(session, res),
                err -> {
                    log.error("Method {} error", req.getMethod(), err);
                    sendError(session, req.getId(), "internal_error", err.getMessage());
                }
            );
        } catch (Exception e) {
            log.error("Method {} unexpected error", req.getMethod(), e);
            sendError(session, req.getId(), "internal_error", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WS connection closed: {} status={}", session.getId(), status);
        sessions.remove(session.getId());
    }

    public void broadcastEvent(String eventType, Object payload) {
        try {
            GatewayFrame frame = GatewayFrame.builder()
                .type("event")
                .event(eventType)
                .payload(JsonUtils.toJson(payload))
                .build();
            String text = objectMapper.writeValueAsString(frame);
            for (WebSocketSession s : sessions.values()) {
                if (s.isOpen()) {
                    try {
                        s.sendMessage(new TextMessage(text));
                    } catch (IOException e) {
                        log.warn("Failed to send event to {}", s.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Broadcast error", e);
        }
    }

    public void sendEvent(WebSocketSession session, String eventType, Object payload) {
        try {
            GatewayFrame frame = GatewayFrame.builder()
                .type("event")
                .event(eventType)
                .payload(JsonUtils.toJson(payload))
                .build();
            sendFrame(session, frame);
        } catch (Exception e) {
            log.error("Send event error", e);
        }
    }

    private void sendFrame(WebSocketSession session, GatewayFrame frame) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
        } catch (IOException e) {
            log.warn("Failed to send frame to {}", session.getId());
        }
    }

    private void sendError(WebSocketSession session, String id, String code, String message) {
        try {
            GatewayFrame frame = GatewayFrame.builder()
                .type("res")
                .id(id)
                .ok(false)
                .error(GatewayError.of(code, message))
                .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
        } catch (IOException e) {
            log.warn("Failed to send error to {}", session.getId());
        }
    }
}
