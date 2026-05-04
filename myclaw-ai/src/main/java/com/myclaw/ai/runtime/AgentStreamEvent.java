package com.myclaw.ai.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentStreamEvent {

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("stream")
    private String stream; // "assistant", "tool", "lifecycle", "error"

    @JsonProperty("delta")
    private String delta;

    @JsonProperty("phase")
    private String phase; // for lifecycle: "start", "end", "error"

    @JsonProperty("text")
    private String text;

    @JsonProperty("error")
    private String error;

    public static AgentStreamEvent assistant(String runId, String delta) {
        return AgentStreamEvent.builder()
            .runId(runId)
            .stream("assistant")
            .delta(delta)
            .build();
    }

    public static AgentStreamEvent lifecycle(String runId, String phase, String text) {
        return AgentStreamEvent.builder()
            .runId(runId)
            .stream("lifecycle")
            .phase(phase)
            .text(text)
            .build();
    }

    public static AgentStreamEvent error(String runId, String error) {
        return AgentStreamEvent.builder()
            .runId(runId)
            .stream("error")
            .error(error)
            .build();
    }
}
