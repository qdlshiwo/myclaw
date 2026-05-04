package com.myclaw.core.model;

import lombok.Data;

@Data
public class AgentContext {

    private String agentId;
    private String name;
    private String workspace;
    private String defaultModel;
}
