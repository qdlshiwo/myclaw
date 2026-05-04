package com.myclaw.server.websocket;

import org.springframework.web.socket.WebSocketSession;

public interface GatewayEventPublisher {
    void sendEvent(WebSocketSession session, String eventType, Object payload);
    void broadcastEvent(String eventType, Object payload);
}
