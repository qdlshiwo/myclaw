package com.myclaw.server.method;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GatewayMethodRegistry {

    private final Map<String, GatewayMethodHandler> handlers = new ConcurrentHashMap<>();

    public GatewayMethodRegistry(List<GatewayMethodHandler> handlerList) {
        for (GatewayMethodHandler h : handlerList) {
            handlers.put(h.getMethod(), h);
        }
    }

    public GatewayMethodHandler getHandler(String method) {
        return handlers.get(method);
    }
}
