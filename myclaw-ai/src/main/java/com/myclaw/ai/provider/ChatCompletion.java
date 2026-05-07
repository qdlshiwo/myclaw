package com.myclaw.ai.provider;

import com.myclaw.core.tool.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletion {
    private String content;
    private List<ToolCall> toolCalls;
}
