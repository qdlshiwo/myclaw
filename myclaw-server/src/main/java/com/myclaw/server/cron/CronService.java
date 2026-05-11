package com.myclaw.server.cron;

import com.myclaw.core.model.CronJob;
import com.myclaw.core.store.CronJobStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cron service that manages scheduled jobs.
 * Uses a single ScheduledExecutorService to check for due jobs periodically.
 */
@Slf4j
@Service
public class CronService {

    private final CronJobStore jobStore;
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> tickFuture;

    public CronService(CronJobStore jobStore) {
        this.jobStore = jobStore;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cron-tick");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void start() {
        // Tick every 1 second to check for due jobs
        tickFuture = scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        log.info("CronService started");
    }

    @PreDestroy
    public void stop() {
        if (tickFuture != null) tickFuture.cancel(false);
        scheduler.shutdownNow();
        log.info("CronService stopped");
    }

    private void tick() {
        try {
            Instant now = Instant.now();
            for (CronJob job : jobStore.listAll()) {
                if (!job.isEnabled()) continue;
                if (isDue(job, now)) {
                    executeJob(job);
                    updateJobAfterRun(job, now);
                }
            }
        } catch (Exception e) {
            log.error("Cron tick error", e);
        }
    }

    private boolean isDue(CronJob job, Instant now) {
        Map<String, Object> state = job.getState() != null ? new HashMap<>(job.getState()) : new HashMap<>();
        Instant lastRunAt = job.getLastRunAt();
        String nextRunAtStr = (String) state.get("nextRunAt");
        Instant nextRunAt = nextRunAtStr != null ? Instant.parse(nextRunAtStr) : null;

        CronJob.Schedule schedule = job.getSchedule();
        if (schedule == null) return false;

        switch (schedule.getKind()) {
            case "at":
                Instant atTime = Instant.parse(schedule.getAt());
                return now.isAfter(atTime) || now.equals(atTime);

            case "every":
                if (nextRunAt == null) return true; // first run
                return now.isAfter(nextRunAt) || now.equals(nextRunAt);

            case "cron":
                // Simple cron evaluation using croner-like logic
                if (nextRunAt == null) {
                    nextRunAt = computeNextCronRun(schedule);
                    if (nextRunAt == null) return false;
                }
                return now.isAfter(nextRunAt) || now.equals(nextRunAt);

            default:
                return false;
        }
    }

    private Instant computeNextCronRun(CronJob.Schedule schedule) {
        // Simplified cron parser: supports "minute hour day month dow"
        // For production, use a proper cron library like cron-utils
        String expr = schedule.getExpr();
        if (expr == null || expr.isBlank()) return null;

        String tzStr = schedule.getTz() != null ? schedule.getTz() : "UTC";
        ZoneId tz = ZoneId.of(tzStr);
        Instant now = Instant.now();

        // Try to parse and compute next run within the next minute
        for (int i = 1; i <= 60; i++) {
            Instant candidate = now.plus(i, java.time.temporal.ChronoUnit.SECONDS);
            if (matchesCron(candidate, expr, tz)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean matchesCron(Instant instant, String expr, ZoneId tz) {
        String[] parts = expr.trim().split("\\s+");
        if (parts.length < 5) return false;

        java.time.ZonedDateTime zdt = instant.atZone(tz);
        int minute = zdt.getMinute();
        int hour = zdt.getHour();
        int dayOfMonth = zdt.getDayOfMonth();
        int month = zdt.getMonthValue();
        int dayOfWeek = zdt.getDayOfWeek().getValue(); // 1=Monday

        return matchField(minute, parts[0], 0, 59)
            && matchField(hour, parts[1], 0, 23)
            && matchField(dayOfMonth, parts[2], 1, 31)
            && matchField(month, parts[3], 1, 12)
            && matchField(dayOfWeek, parts[4], 1, 7);
    }

    private boolean matchField(int value, String pattern, int min, int max) {
        if ("*".equals(pattern)) return true;
        if (pattern.contains(",")) {
            for (String part : pattern.split(",")) {
                if (matchField(value, part, min, max)) return true;
            }
            return false;
        }
        if (pattern.contains("-")) {
            String[] range = pattern.split("-");
            int start = Integer.parseInt(range[0]);
            int end = Integer.parseInt(range[1]);
            return value >= start && value <= end;
        }
        if (pattern.contains("/")) {
            String[] parts = pattern.split("/");
            int step = Integer.parseInt(parts[1]);
            int base = "*".equals(parts[0]) ? min : Integer.parseInt(parts[0]);
            return value >= base && (value - base) % step == 0;
        }
        return value == Integer.parseInt(pattern);
    }

    private void executeJob(CronJob job) {
        log.info("Executing cron job: {} ({})", job.getName(), job.getId());
        CronJob.Payload payload = job.getPayload();
        if (payload == null) return;

        if ("systemEvent".equals(payload.getKind())) {
            // System event: just log it for now
            log.info("System event: {}", payload.getText());
        } else if ("agentTurn".equals(payload.getKind())) {
            // For agent turn, we'll integrate with AgentLoopService in a future step
            log.info("Agent turn triggered: model={}, message={}",
                payload.getModel(), payload.getMessage());
        }
    }

    private void updateJobAfterRun(CronJob job, Instant runTime) {
        CronJob.Schedule schedule = job.getSchedule();
        if (schedule == null) return;

        job.setLastRunAt(runTime);
        job.setLastRunStatus("success");
        job.setConsecutiveErrors(0);

        Map<String, Object> state = job.getState() != null ? new HashMap<>(job.getState()) : new HashMap<>();

        switch (schedule.getKind()) {
            case "at":
                // One-shot: disable after run
                job.setEnabled(false);
                state.put("nextRunAt", null);
                break;

            case "every":
                // Set next run time
                Instant nextRun = runTime.plusMillis(schedule.getEveryMs());
                state.put("nextRunAt", nextRun.toString());
                break;

            case "cron":
                Instant nextCron = computeNextCronRun(schedule);
                state.put("nextRunAt", nextCron != null ? nextCron.toString() : null);
                break;
        }

        job.setState(state);
        jobStore.save(job);
    }

    // Public API
    public CronJob addJob(CronJob job) {
        CronJob.Schedule schedule = job.getSchedule();
        if (schedule != null && "cron".equals(schedule.getKind())) {
            Instant nextRun = computeNextCronRun(schedule);
            Map<String, Object> state = new HashMap<>();
            state.put("nextRunAt", nextRun != null ? nextRun.toString() : null);
            job.setState(state);
        }
        return jobStore.save(job);
    }

    public CronJob updateJob(CronJob job) {
        return jobStore.save(job);
    }

    public boolean deleteJob(String id) {
        return jobStore.delete(id);
    }

    public CronJob runJobNow(String id) {
        return jobStore.get(id).map(job -> {
            executeJob(job);
            updateJobAfterRun(job, Instant.now());
            return job;
        }).orElse(null);
    }

    public CronJob toggleJob(String id) {
        return jobStore.get(id).map(job -> {
            job.setEnabled(!job.isEnabled());
            jobStore.save(job);
            return job;
        }).orElse(null);
    }
}
