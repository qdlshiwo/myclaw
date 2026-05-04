package com.myclaw.ai.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StreamChunk {

    public enum Type {
        CONTENT,
        TOOL_CALL,
        FINISH,
        ERROR
    }

    private Type type;
    private String content;
    private String toolName;
    private String toolArguments;
    private String finishReason;
    private String errorMessage;
}
