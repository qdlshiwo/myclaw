package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.model.CronJob;
import com.myclaw.core.protocol.GatewayFrame;
import com.myclaw.core.protocol.GatewayError;
import com.myclaw.server.cron.CronService;
import com.myclaw.server.cron.FileCronJobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CronMethodHandler implements GatewayMethodHandler {

    private final CronService cronService;
    private final FileCronJobStore jobStore;
    private final ObjectMapper objectMapper;

    public CronMethodHandler(CronService cronService, FileCronJobStore jobStore, ObjectMapper objectMapper) {
        this.cronService = cronService;
        this.jobStore = jobStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getMethod() {
        return "cron";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        JsonNode params = request.getParams();
        String action = params.path("action").asText("list");

        try {
            return switch (action) {
                case "list" -> handleList(session, request);
                case "add" -> handleAdd(session, request, params);
                case "update" -> handleUpdate(session, request, params);
                case "delete" -> handleDelete(session, request, params);
                case "run" -> handleRunNow(session, request, params);
                case "toggle" -> handleToggle(session, request, params);
                default -> Mono.just(errorFrame(request, "unknown_action", "Unknown action: " + action));
            };
        } catch (Exception e) {
            return Mono.just(errorFrame(request, "internal_error", e.getMessage()));
        }
    }

    private Mono<GatewayFrame> handleList(WebSocketSession session, GatewayFrame request) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode arr = payload.putArray("jobs");
        for (CronJob job : jobStore.listAll()) {
            ObjectNode obj = arr.addObject();
            obj.put("id", job.getId());
            obj.put("name", job.getName());
            obj.put("description", job.getDescription());
            obj.put("enabled", job.isEnabled());
            obj.put("agentId", job.getAgentId());
            obj.put("sessionKey", job.getSessionKey());
            obj.put("sessionTarget", job.getSessionTarget());
            obj.put("lastRunAt", job.getLastRunAt() != null ? job.getLastRunAt().toString() : null);
            obj.put("lastRunStatus", job.getLastRunStatus());
            obj.put("consecutiveErrors", job.getConsecutiveErrors());
            obj.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
            if (job.getSchedule() != null) {
                ObjectNode sched = obj.putObject("schedule");
                sched.put("kind", job.getSchedule().getKind());
                sched.put("expr", job.getSchedule().getExpr());
                sched.put("tz", job.getSchedule().getTz());
                sched.put("at", job.getSchedule().getAt());
                sched.put("everyMs", job.getSchedule().getEveryMs());
            }
            if (job.getPayload() != null) {
                ObjectNode pay = obj.putObject("payload");
                pay.put("kind", job.getPayload().getKind());
                pay.put("message", job.getPayload().getMessage());
                pay.put("text", job.getPayload().getText());
                pay.put("model", job.getPayload().getModel());
            }
        }
        return Mono.just(successFrame(request, payload));
    }

    private Mono<GatewayFrame> handleAdd(WebSocketSession session, GatewayFrame request, JsonNode params) {
        CronJob job = parseJob(params.path("job"));
        if (job.getName() == null || job.getName().isBlank()) {
            return Mono.just(errorFrame(request, "invalid_params", "Job name is required"));
        }
        if (job.getSchedule() == null) {
            return Mono.just(errorFrame(request, "invalid_params", "Job schedule is required"));
        }
        if (job.getPayload() == null) {
            return Mono.just(errorFrame(request, "invalid_params", "Job payload is required"));
        }
        CronJob saved = cronService.addJob(job);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("job", saved);
        return Mono.just(successFrame(request, payload));
    }

    private Mono<GatewayFrame> handleUpdate(WebSocketSession session, GatewayFrame request, JsonNode params) {
        CronJob job = parseJob(params.path("job"));
        if (job.getId() == null) {
            return Mono.just(errorFrame(request, "invalid_params", "Job id is required"));
        }
        CronJob saved = cronService.updateJob(job);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("job", saved);
        return Mono.just(successFrame(request, payload));
    }

    private Mono<GatewayFrame> handleDelete(WebSocketSession session, GatewayFrame request, JsonNode params) {
        String id = params.path("id").asText(null);
        if (id == null) {
            return Mono.just(errorFrame(request, "invalid_params", "Job id is required"));
        }
        boolean ok = cronService.deleteJob(id);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("deleted", ok ? 1 : 0);
        return Mono.just(successFrame(request, payload));
    }

    private Mono<GatewayFrame> handleRunNow(WebSocketSession session, GatewayFrame request, JsonNode params) {
        String id = params.path("id").asText(null);
        if (id == null) {
            return Mono.just(errorFrame(request, "invalid_params", "Job id is required"));
        }
        CronJob job = cronService.runJobNow(id);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("job", job);
        return Mono.just(successFrame(request, payload));
    }

    private Mono<GatewayFrame> handleToggle(WebSocketSession session, GatewayFrame request, JsonNode params) {
        String id = params.path("id").asText(null);
        if (id == null) {
            return Mono.just(errorFrame(request, "invalid_params", "Job id is required"));
        }
        CronJob job = cronService.toggleJob(id);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("job", job);
        return Mono.just(successFrame(request, payload));
    }

    private CronJob parseJob(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, CronJob.class);
        } catch (Exception e) {
            log.warn("Failed to parse job", e);
            return new CronJob();
        }
    }

    private GatewayFrame successFrame(GatewayFrame request, ObjectNode payload) {
        return GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(true)
            .payload(payload)
            .build();
    }

    private GatewayFrame errorFrame(GatewayFrame request, String code, String message) {
        return GatewayFrame.builder()
            .type("res")
            .id(request.getId())
            .ok(false)
            .error(GatewayError.of(code, message))
            .build();
    }
}
