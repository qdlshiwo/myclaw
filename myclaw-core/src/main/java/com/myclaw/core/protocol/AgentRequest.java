package com.myclaw.core.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AgentRequest {

    @JsonProperty("message")
    private String message;

    @JsonProperty("sessionKey")
    private String sessionKey;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("model")
    private String model;

    @JsonProperty("thinking")
    private Boolean thinking;

    @JsonProperty("idempotencyKey")
    private String idempotencyKey;
}
