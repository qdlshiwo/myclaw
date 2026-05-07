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
public class FileReadTool implements Tool {

    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("path", objectMapper.createObjectNode()
            .put("type", "string")
            .put("description", "Absolute or relative file path to read."));

        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "object");
        params.set("properties", properties);
        params.set("required", objectMapper.createArrayNode().add("path"));

        return ToolDefinition.builder()
            .name(getName())
            .description("Read the contents of a file at the given path.")
            .parameters(params)
            .build();
    }

    @Override
    public String execute(String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            String path = args.path("path").asText("");
            if (path.isEmpty()) {
                return "Error: path is required";
            }
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return "Error: file does not exist: " + path;
            }
            if (Files.isDirectory(filePath)) {
                return "Error: path is a directory: " + path;
            }
            String content = Files.readString(filePath);
            return content;
        } catch (IOException e) {
            log.error("Failed to read file", e);
            return "Error: " + e.getMessage();
        }
    }
}
