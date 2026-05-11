package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ConnectMethodHandler implements GatewayMethodHandler {

    private final ObjectMapper objectMapper;

    public ConnectMethodHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getMethod() {
        return "connect";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        JsonNode params = request.getParams();
        String deviceId = params.path("deviceId").asText("unknown");
        String platform = params.path("platform").asText("web");

        log.info("Client connected: deviceId={}, platform={}", deviceId, platform);

        // Build hello-ok payload with available methods and events
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("hello", "ok");

        ObjectNode features = payload.putObject("features");
        ArrayNode methods = features.putArray("methods");
        methods.add("health");
        methods.add("status");
        methods.add("send");
        methods.add("agent");
        methods.add("agent.wait");
        methods.add("chat");
        methods.add("sessions");
        methods.add("config");
        methods.add("models");
        methods.add("providers");
        methods.add("file");
        methods.add("cron");

        ArrayNode events = features.putArray("events");
        events.add("tick");
        events.add("agent");
        events.add("chat");
        events.add("presence");
        events.add("health");
        events.add("heartbeat");

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }
}
