package com.myclaw.core.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentResponse {

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("acceptedAt")
    private String acceptedAt;
}
