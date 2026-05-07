package com.myclaw.ai.tool;

import com.myclaw.core.tool.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> toolBeans) {
        for (Tool tool : toolBeans) {
            register(tool);
        }
    }

    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
        log.info("Registered tool: {}", tool.getName());
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<ToolDefinition> listDefinitions() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (Tool tool : tools.values()) {
            defs.add(tool.getDefinition());
        }
        return defs;
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }
}
