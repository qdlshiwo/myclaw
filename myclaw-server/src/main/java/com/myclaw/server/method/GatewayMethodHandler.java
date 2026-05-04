package com.myclaw.server.method;

import com.myclaw.core.protocol.GatewayFrame;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

public interface GatewayMethodHandler {

    String getMethod();

    Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request);
}
