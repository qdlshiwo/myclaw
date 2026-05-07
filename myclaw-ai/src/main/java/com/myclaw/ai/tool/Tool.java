package com.myclaw.ai.tool;

import com.myclaw.core.tool.ToolDefinition;

public interface Tool {
    String getName();
    ToolDefinition getDefinition();
    String execute(String arguments);
}
