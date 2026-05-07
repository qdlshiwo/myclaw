package com.myclaw.core.store;

import com.myclaw.core.model.Session;
import com.myclaw.core.protocol.ChatMessage;

public interface SessionStore {
    void save(Session session);
    void addMessage(Session session, ChatMessage message);
}
