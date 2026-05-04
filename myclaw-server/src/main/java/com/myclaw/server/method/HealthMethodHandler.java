package com.myclaw.server.method;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.core.util.JsonUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class HealthMethodHandler implements GatewayMethodHandler {

    @Override
    public String getMethod() {
        return "health";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        ObjectNode payload = JsonUtils.objectNode();
        payload.put("status", "ok");
        payload.put("version", "0.1.0");

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }
}
