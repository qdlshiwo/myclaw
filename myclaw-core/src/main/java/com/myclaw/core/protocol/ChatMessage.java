package com.myclaw.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private String content;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("toolCalls")
    private String toolCalls;

    @JsonProperty("toolCallId")
    private String toolCallId;
}
