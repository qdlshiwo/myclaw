package com.myclaw.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.tool.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileWriteTool implements Tool {

    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("path", objectMapper.createObjectNode()
            .put("type", "string")
            .put("description", "Absolute or relative file path to write."));
        properties.set("content", objectMapper.createObjectNode()
            .put("type", "string")
            .put("description", "Content to write to the file."));

        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "object");
        params.set("properties", properties);
        params.set("required", objectMapper.createArrayNode().add("path").add("content"));

        return ToolDefinition.builder()
            .name(getName())
            .description("Write content to a file at the given path. Creates parent directories if needed.")
            .parameters(params)
            .build();
    }

    @Override
    public String execute(String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            String path = args.path("path").asText("");
            String content = args.path("content").asText("");
            if (path.isEmpty()) {
                return "Error: path is required";
            }
            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            return "File written successfully: " + path;
        } catch (IOException e) {
            log.error("Failed to write file", e);
            return "Error: " + e.getMessage();
        }
    }
}
