package com.myclaw.server.session;

import com.myclaw.core.model.Session;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemorySessionManager {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public Session getOrCreate(String sessionKey, String agentId) {
        return sessions.computeIfAbsent(sessionKey, k -> Session.create(sessionKey, agentId));
    }

    public Session get(String sessionKey) {
        return sessions.get(sessionKey);
    }

    public Collection<Session> listAll() {
        return sessions.values();
    }

    public void clear() {
        sessions.clear();
    }
}
