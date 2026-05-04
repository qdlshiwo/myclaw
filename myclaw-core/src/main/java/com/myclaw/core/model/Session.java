package com.myclaw.core.model;

import com.myclaw.core.protocol.ChatMessage;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class Session {

    private String sessionId;
    private String sessionKey;
    private String agentId;
    private List<ChatMessage> messages = new ArrayList<>();
    private Instant createdAt;
    private Instant lastInteractionAt;
    private boolean active;

    public static Session create(String sessionKey, String agentId) {
        Session s = new Session();
        s.sessionId = UUID.randomUUID().toString();
        s.sessionKey = sessionKey;
        s.agentId = agentId;
        s.createdAt = Instant.now();
        s.lastInteractionAt = Instant.now();
        s.active = true;
        return s;
    }
}
