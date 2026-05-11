package com.myclaw.server.cron;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.myclaw.core.model.CronJob;
import com.myclaw.core.store.CronJobStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FileCronJobStore implements CronJobStore {

    private final Path jobsFile;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CronJob> jobs = new ConcurrentHashMap<>();

    public FileCronJobStore() {
        String userHome = System.getProperty("user.home");
        this.jobsFile = Path.of(userHome, ".qoderwork", "myclaw", "cron-jobs.json");
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(jobsFile.getParent());
            if (Files.exists(jobsFile)) {
                String json = Files.readString(jobsFile);
                Map<String, CronJob> loaded = objectMapper.readValue(json, new TypeReference<>() {});
                jobs.putAll(loaded);
                log.info("Loaded {} cron jobs from {}", jobs.size(), jobsFile);
            } else {
                saveToFile();
            }
        } catch (IOException e) {
            log.warn("Failed to load cron jobs from {}", jobsFile, e);
        }
    }

    @Override
    public Collection<CronJob> listAll() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    @Override
    public Optional<CronJob> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public CronJob save(CronJob job) {
        job.setUpdatedAt(java.time.Instant.now());
        jobs.put(job.getId(), job);
        saveToFile();
        return job;
    }

    @Override
    public boolean delete(String id) {
        CronJob removed = jobs.remove(id);
        if (removed != null) {
            saveToFile();
            return true;
        }
        return false;
    }

    @Override
    public void updateState(String id, Map<String, Object> state) {
        CronJob job = jobs.get(id);
        if (job != null) {
            job.setState(state);
        }
    }

    private synchronized void saveToFile() {
        try {
            Files.createDirectories(jobsFile.getParent());
            Path tmp = jobsFile.resolveSibling("cron-jobs.json.tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), jobs);
            Files.move(tmp, jobsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Failed to save cron jobs to {}", jobsFile, e);
        }
    }
}
