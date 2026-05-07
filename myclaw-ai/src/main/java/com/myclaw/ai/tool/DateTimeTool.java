package com.myclaw.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.tool.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class DateTimeTool implements Tool {

    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "get_datetime";
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("timezone", objectMapper.createObjectNode()
            .put("type", "string")
            .put("description", "Timezone identifier, e.g. Asia/Shanghai, UTC, America/New_York."));

        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "object");
        params.set("properties", properties);
        params.set("required", objectMapper.createArrayNode());

        return ToolDefinition.builder()
            .name(getName())
            .description("Get the current date and time in the specified timezone. Defaults to Asia/Shanghai.")
            .parameters(params)
            .build();
    }

    @Override
    public String execute(String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            String tz = args.path("timezone").asText("Asia/Shanghai");
            ZoneId zone = ZoneId.of(tz);
            String now = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE")
                .withZone(zone)
                .format(Instant.now());
            return now + " (" + tz + ")";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
