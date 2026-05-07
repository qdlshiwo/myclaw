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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Component
public class InMemorySessionManager implements SessionStore {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
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
        try (Stream<Path> paths = Files.list(sessionsDir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadSession);
            log.info("Loaded {} sessions from {}", sessions.size(), sessionsDir);
        } catch (IOException e) {
            log.warn("Failed to load sessions from {}", sessionsDir, e);
        }
    }

    private void loadSession(Path path) {
        try {
            String json = Files.readString(path);
            Session s = objectMapper.readValue(json, Session.class);
            if (s.getSessionKey() != null) {
                sessions.put(s.getSessionKey(), s);
            }
        } catch (IOException e) {
            log.warn("Failed to load session from {}", path, e);
        }
    }

    public Session getOrCreate(String sessionKey, String agentId) {
        Session s = sessions.computeIfAbsent(sessionKey, k -> Session.create(sessionKey, agentId));
        save(s);
        return s;
    }

    public Session get(String sessionKey) {
        return sessions.get(sessionKey);
    }

    public Collection<Session> listAll() {
        return sessions.values();
    }

    public void clear() {
        sessions.clear();
        try {
            if (Files.exists(sessionsDir)) {
                try (Stream<Path> paths = Files.list(sessionsDir)) {
                    paths.forEach(p -> {
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
        try {
            Files.createDirectories(sessionsDir);
            Path path = sessionsDir.resolve(session.getSessionKey() + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), session);
        } catch (IOException e) {
            log.warn("Failed to save session {}", session.getSessionKey(), e);
        }
    }

    @Override
    public void addMessage(Session session, ChatMessage message) {
        if (session != null) {
            session.getMessages().add(message);
            save(session);
        }
    }
}
