package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.ai.runtime.AgentLoopService;
import com.myclaw.ai.runtime.AgentStreamEvent;
import com.myclaw.core.model.AgentContext;
import com.myclaw.core.model.Session;
import com.myclaw.core.protocol.AgentRequest;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.server.config.ConfigStore;
import com.myclaw.server.session.InMemorySessionManager;
import com.myclaw.server.websocket.GatewayEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class AgentMethodHandler implements GatewayMethodHandler {

    private final AgentLoopService agentLoopService;
    private final InMemorySessionManager sessionManager;
    private final GatewayEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ConfigStore configStore;

    public AgentMethodHandler(AgentLoopService agentLoopService,
                              InMemorySessionManager sessionManager,
                              GatewayEventPublisher eventPublisher,
                              ObjectMapper objectMapper,
                              ConfigStore configStore) {
        this.agentLoopService = agentLoopService;
        this.sessionManager = sessionManager;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.configStore = configStore;
    }

    @Override
    public String getMethod() {
        return "agent";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        AgentRequest req = parseParams(request.getParams());
        String runId = UUID.randomUUID().toString();
        String sessionKey = req.getSessionKey() != null ? req.getSessionKey() : "main";
        String agentId = "main";

        // Resolve or create session
        Session sess = sessionManager.getOrCreate(sessionKey, agentId);

        // Build agent context
        AgentContext agent = new AgentContext();
        agent.setAgentId(agentId);
        agent.setName("MyClaw");
        agent.setDefaultModel(req.getModel());

        // Return accepted immediately, then stream events
        String acceptedAt = Instant.now().toString();

        // Start agent loop in background and stream events to WS
        agentLoopService.run(runId, agent, sess, req.getMessage(), req.getModel(), configStore.toProviderConfig())
            .publishOn(Schedulers.boundedElastic())
            .subscribe(
                event -> eventPublisher.sendEvent(session, "agent", event),
                err -> log.error("Agent stream error for runId={}", runId, err),
                () -> log.debug("Agent stream completed for runId={}", runId)
            );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("runId", runId);
        payload.put("status", "accepted");
        payload.put("acceptedAt", acceptedAt);

        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build());
    }

    private AgentRequest parseParams(JsonNode params) {
        AgentRequest req = new AgentRequest();
        req.setMessage(params.path("message").asText(""));
        req.setSessionKey(params.path("sessionKey").asText(null));
        req.setSessionId(params.path("sessionId").asText(null));
        req.setModel(params.path("model").asText("gpt-4o-mini"));
        req.setThinking(params.path("thinking").asBoolean(false));
        req.setIdempotencyKey(params.path("idempotencyKey").asText(null));
        return req;
    }
}
