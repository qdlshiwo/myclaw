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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileListTool implements Tool {

    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("path", objectMapper.createObjectNode()
            .put("type", "string")
            .put("description", "Directory path to list. Defaults to current directory."));

        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "object");
        params.set("properties", properties);
        params.set("required", objectMapper.createArrayNode().add("path"));

        return ToolDefinition.builder()
            .name(getName())
            .description("List files and directories in the given directory path.")
            .parameters(params)
            .build();
    }

    @Override
    public String execute(String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            String path = args.path("path").asText(".");
            Path dirPath = Paths.get(path);
            if (!Files.exists(dirPath)) {
                return "Error: directory does not exist: " + path;
            }
            if (!Files.isDirectory(dirPath)) {
                return "Error: path is not a directory: " + path;
            }
            try (Stream<Path> stream = Files.list(dirPath)) {
                return stream.map(p -> {
                    String name = p.getFileName().toString();
                    if (Files.isDirectory(p)) {
                        return name + "/";
                    }
                    return name;
                }).collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            log.error("Failed to list files", e);
            return "Error: " + e.getMessage();
        }
    }
}
