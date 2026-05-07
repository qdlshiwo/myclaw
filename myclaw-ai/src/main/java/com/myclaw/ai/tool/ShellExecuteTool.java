package com.myclaw.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.tool.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShellExecuteTool implements Tool {

    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "execute_shell";
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("command", objectMapper.createObjectNode()
            .put("type", "string")
            .put("description", "Shell command to execute."));
        properties.set("timeout", objectMapper.createObjectNode()
            .put("type", "integer")
            .put("description", "Timeout in seconds. Default is 30."));

        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "object");
        params.set("properties", properties);
        params.set("required", objectMapper.createArrayNode().add("command"));

        return ToolDefinition.builder()
            .name(getName())
            .description("Execute a shell command and return the output. Use with caution.")
            .parameters(params)
            .build();
    }

    @Override
    public String execute(String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            String command = args.path("command").asText("");
            int timeout = args.path("timeout").asInt(30);
            if (command.isEmpty()) {
                return "Error: command is required";
            }

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: command timed out after " + timeout + " seconds";
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return "Exit code " + exitCode + ":\n" + output;
            }
            return output.toString().trim();
        } catch (Exception e) {
            log.error("Failed to execute shell command", e);
            return "Error: " + e.getMessage();
        }
    }
}
