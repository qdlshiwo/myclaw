package com.myclaw.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a scheduled cron job that triggers an agent turn.
 * Modeled after openclaw's CronJob with three schedule types: cron, at, every.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CronJob {

    private String id;
    private String name;
    private String description;
    private boolean enabled;
    private String agentId;
    private String sessionKey;
    private String sessionTarget;  // "main" | "isolated" | "current" | "session:<id>"
    private Schedule schedule;
    private Payload payload;
    private Map<String, Object> state;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastRunAt;
    private String lastRunStatus;  // "success", "error", "skipped"
    private int consecutiveErrors;

    /**
     * Schedule configuration supporting three types.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Schedule {
        private String kind;      // "cron" | "at" | "every"
        private String expr;      // cron expression (e.g. "0 9 * * *")
        private String tz;        // timezone (e.g. "Asia/Shanghai")
        private String at;        // ISO-8601 timestamp for one-shot
        private long everyMs;     // interval in milliseconds for recurring
        private Long anchorMs;    // anchor time for "every" schedules
    }

    /**
     * Payload configuration for agent turn.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payload {
        private String kind;       // "agentTurn" | "systemEvent"
        private String message;    // message for agent turn
        private String text;       // text for system event
        private String model;      // optional model override
        private List<String> allowedTools; // optional tool allowlist
    }

    /**
     * Create a new CronJob with auto-generated ID and timestamps.
     */
    public static CronJob create(String name, Schedule schedule, Payload payload) {
        CronJob job = new CronJob();
        job.id = UUID.randomUUID().toString();
        job.name = name;
        job.enabled = true;
        job.agentId = "main";
        job.sessionKey = "main";
        job.sessionTarget = "isolated";
        job.schedule = schedule;
        job.payload = payload;
        job.state = Map.of();
        job.consecutiveErrors = 0;
        job.createdAt = Instant.now();
        job.updatedAt = Instant.now();
        return job;
    }
}
