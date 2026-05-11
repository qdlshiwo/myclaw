package com.myclaw.server.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.myclaw.core.model.Session;
import com.myclaw.core.protocol.ChatMessage;
import com.myclaw.core.store.SessionStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Slf4j
@Component
public class InMemorySessionManager implements SessionStore {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Path sessionsDir;
    private final ObjectMapper objectMapper;

    public InMemorySessionManager() {
        String userHome = System.getProperty("user.home");
        this.sessionsDir = Path.of(userHome, ".qoderwork", "myclaw", "sessions");
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        if (!Files.exists(sessionsDir)) {
            return;
        }
        try {
            List<Path> allFiles;
            try (Stream<Path> s = Files.list(sessionsDir)) {
                allFiles = s.toList();
            }
            Set<String> jsonlKeys = new HashSet<>();
            for (Path p : allFiles) {
                if (p.toString().endsWith(".jsonl")) {
                    loadSessionJsonl(p);
                    jsonlKeys.add(stripExtension(p.getFileName().toString(), ".jsonl"));
                }
            }
            for (Path p : allFiles) {
                if (p.toString().endsWith(".json")) {
                    String key = stripExtension(p.getFileName().toString(), ".json");
                    if (!jsonlKeys.contains(key)) {
                        migrateJsonToJsonl(p);
                    }
                }
            }
            log.info("Loaded {} sessions from {}", sessions.size(), sessionsDir);
        } catch (IOException e) {
            log.warn("Failed to load sessions from {}", sessionsDir, e);
        }
    }

    private String stripExtension(String filename, String ext) {
        if (filename.endsWith(ext)) {
            return filename.substring(0, filename.length() - ext.length());
        }
        return filename;
    }

    private void loadSessionJsonl(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty()) return;

            String headerLine = lines.get(0).trim();
            if (headerLine.isEmpty()) return;

            Map<String, Object> header = objectMapper.readValue(headerLine, new TypeReference<>() {});
            String sessionKey = (String) header.get("sessionKey");
            String agentId = (String) header.get("agentId");
            String sessionId = (String) header.get("sessionId");

            Session s = new Session();
            s.setSessionKey(sessionKey);
            s.setAgentId(agentId);
            s.setSessionId(sessionId);
            String createdAtStr = (String) header.get("createdAt");
            if (createdAtStr != null) {
                s.setCreatedAt(Instant.parse(createdAtStr));
            }
            s.setLastInteractionAt(Instant.now());
            s.setActive(true);

            List<ChatMessage> messages = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                ChatMessage msg = objectMapper.readValue(line, ChatMessage.class);
                messages.add(msg);
            }
            s.setMessages(messages);
            if (sessionKey != null) {
                sessions.put(sessionKey, s);
            }
        } catch (IOException e) {
            log.warn("Failed to load session from {}", path, e);
        }
    }

    private void migrateJsonToJsonl(Path jsonPath) {
        try {
            String json = Files.readString(jsonPath);
            Session s = objectMapper.readValue(json, Session.class);
            if (s.getSessionKey() != null) {
                sessions.put(s.getSessionKey(), s);
                save(s);
                Files.deleteIfExists(jsonPath);
                log.info("Migrated session {} from .json to .jsonl", s.getSessionKey());
            }
        } catch (IOException e) {
            log.warn("Failed to migrate session from {}", jsonPath, e);
        }
    }

    public Session getOrCreate(String sessionKey, String agentId) {
        Session s = sessions.computeIfAbsent(sessionKey, k -> Session.create(sessionKey, agentId));
        Path path = sessionsDir.resolve(sessionKey + ".jsonl");
        if (!Files.exists(path)) {
            save(s);
        }
        return s;
    }

    public Session get(String sessionKey) {
        return sessions.get(sessionKey);
    }

    public Collection<Session> listAll() {
        return sessions.values();
    }

    /**
     * List sessions with pagination and optional fuzzy search.
     */
    public List<Session> listPaged(int page, int pageSize, String searchQuery) {
        List<Session> sorted = sessions.values().stream()
            .sorted(Comparator.comparing(Session::getLastInteractionAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        if (searchQuery != null && !searchQuery.isBlank()) {
            String q = searchQuery.toLowerCase();
            sorted = sorted.stream()
                .filter(s -> s.getSessionKey().toLowerCase().contains(q)
                    || s.getAgentId().toLowerCase().contains(q)
                    || s.getMessages().stream().anyMatch(m ->
                        m.getContent() != null && m.getContent().toLowerCase().contains(q)))
                .toList();
        }

        int start = Math.max(0, page * pageSize);
        int end = Math.min(start + pageSize, sorted.size());
        return start >= sorted.size() ? List.of() : sorted.subList(start, end);
    }

    /**
     * Total session count (optionally filtered).
     */
    public long countAll(String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return sessions.size();
        }
        String q = searchQuery.toLowerCase();
        return sessions.values().stream()
            .filter(s -> s.getSessionKey().toLowerCase().contains(q)
                || s.getAgentId().toLowerCase().contains(q)
                || s.getMessages().stream().anyMatch(m ->
                    m.getContent() != null && m.getContent().toLowerCase().contains(q)))
            .count();
    }

    /**
     * Delete a single session (both from memory and disk).
     */
    public boolean delete(String sessionKey) {
        Session removed = sessions.remove(sessionKey);
        locks.remove(sessionKey);
        if (removed != null) {
            try {
                Path path = sessionsDir.resolve(sessionKey + ".jsonl");
                Files.deleteIfExists(path);
                Path tmpPath = sessionsDir.resolve(sessionKey + ".jsonl.tmp");
                Files.deleteIfExists(tmpPath);
                log.info("Deleted session {} ({} messages)", sessionKey, removed.getMessages().size());
                return true;
            } catch (IOException e) {
                log.warn("Failed to delete session file for {}", sessionKey, e);
            }
        }
        return false;
    }

    /**
     * Delete multiple sessions.
     */
    public int deleteAll(Collection<String> sessionKeys) {
        int count = 0;
        for (String key : sessionKeys) {
            if (delete(key)) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        sessions.clear();
        locks.clear();
        try {
            if (Files.exists(sessionsDir)) {
                try (Stream<Path> paths = Files.list(sessionsDir)) {
                    paths.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".jsonl") || n.endsWith(".jsonl.tmp") || n.endsWith(".json");
                    }).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete session file {}", p, e);
                        }
                    });
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clear sessions dir", e);
        }
    }

    @Override
    public void save(Session session) {
        if (session == null || session.getSessionKey() == null) {
            return;
        }
        ReentrantLock lock = getLock(session.getSessionKey());
        lock.lock();
        try {
            Files.createDirectories(sessionsDir);
            Path target = sessionsDir.resolve(session.getSessionKey() + ".jsonl");
            Path tmp = sessionsDir.resolve(session.getSessionKey() + ".jsonl.tmp");

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("type", "session");
            header.put("sessionKey", session.getSessionKey());
            header.put("agentId", session.getAgentId());
            header.put("sessionId", session.getSessionId());
            header.put("createdAt", session.getCreatedAt() != null ? session.getCreatedAt().toString() : null);

            try (BufferedWriter writer = Files.newBufferedWriter(tmp)) {
                writer.write(objectMapper.writeValueAsString(header));
                writer.newLine();
                for (ChatMessage msg : session.getMessages()) {
                    writer.write(toMessageLine(msg));
                    writer.newLine();
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Failed to save session {}", session.getSessionKey(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addMessage(Session session, ChatMessage message) {
        if (session == null || message == null) {
            return;
        }
        session.getMessages().add(message);
        session.setLastInteractionAt(Instant.now());

        String key = session.getSessionKey();
        if (key == null) return;

        ReentrantLock lock = getLock(key);
        lock.lock();
        try {
            Files.createDirectories(sessionsDir);
            Path target = sessionsDir.resolve(key + ".jsonl");

            if (!Files.exists(target)) {
                Map<String, Object> header = new LinkedHashMap<>();
                header.put("type", "session");
                header.put("sessionKey", key);
                header.put("agentId", session.getAgentId());
                header.put("sessionId", session.getSessionId());
                header.put("createdAt", session.getCreatedAt() != null ? session.getCreatedAt().toString() : null);

                try (BufferedWriter writer = Files.newBufferedWriter(target)) {
                    writer.write(objectMapper.writeValueAsString(header));
                    writer.newLine();
                    writer.write(toMessageLine(message));
                    writer.newLine();
                }
            } else {
                try (BufferedWriter writer = Files.newBufferedWriter(target,
                        StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
                    writer.write(toMessageLine(message));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            log.warn("Failed to append message to session {}", key, e);
        } finally {
            lock.unlock();
        }
    }

    private String toMessageLine(ChatMessage msg) throws IOException {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "message");
        entry.put("role", msg.getRole());
        entry.put("content", msg.getContent());
        entry.put("timestamp", msg.getTimestamp());
        entry.put("runId", msg.getRunId());
        entry.put("toolCalls", msg.getToolCalls());
        entry.put("toolCallId", msg.getToolCallId());
        return objectMapper.writeValueAsString(entry);
    }

    private ReentrantLock getLock(String sessionKey) {
        return locks.computeIfAbsent(sessionKey, k -> new ReentrantLock());
    }
}
