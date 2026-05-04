package com.myclaw.server.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myclaw.core.protocol.GatewayFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileMethodHandler implements GatewayMethodHandler {

    private final ObjectMapper objectMapper;
    private final Path workspaceRoot;

    public FileMethodHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.workspaceRoot = Paths.get(System.getProperty("user.home"), "myclaw-workspace").toAbsolutePath().normalize();
        try {
            Files.createDirectories(workspaceRoot);
        } catch (IOException e) {
            log.warn("Failed to create workspace root: {}", workspaceRoot, e);
        }
    }

    @Override
    public String getMethod() {
        return "file";
    }

    @Override
    public Mono<GatewayFrame> handle(WebSocketSession session, GatewayFrame request) {
        JsonNode params = request.getParams();
        String action = params.path("action").asText("read");
        String relativePath = params.path("path").asText("");

        Path target = resolveSafe(relativePath);
        if (target == null) {
            return error(request, "invalid_path", "Path is outside workspace: " + relativePath);
        }

        try {
            switch (action) {
                case "read" -> {
                    if (!Files.exists(target)) {
                        return error(request, "not_found", "File not found: " + relativePath);
                    }
                    if (Files.isDirectory(target)) {
                        ObjectNode payload = objectMapper.createObjectNode();
                        payload.put("path", relativePath);
                        payload.put("type", "directory");
                        ArrayNode arr = payload.putArray("entries");
                        try (Stream<Path> list = Files.list(target)) {
                            for (Path p : list.sorted().collect(Collectors.toList())) {
                                ObjectNode entry = arr.addObject();
                                entry.put("name", p.getFileName().toString());
                                entry.put("isDirectory", Files.isDirectory(p));
                                entry.put("size", Files.size(p));
                            }
                        }
                        return ok(request, payload);
                    }
                    String content = Files.readString(target, StandardCharsets.UTF_8);
                    ObjectNode payload = objectMapper.createObjectNode();
                    payload.put("path", relativePath);
                    payload.put("type", "file");
                    payload.put("content", content);
                    return ok(request, payload);
                }
                case "write" -> {
                    String content = params.path("content").asText("");
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    ObjectNode payload = objectMapper.createObjectNode();
                    payload.put("path", relativePath);
                    payload.put("written", true);
                    return ok(request, payload);
                }
                case "list" -> {
                    ObjectNode payload = objectMapper.createObjectNode();
                    payload.put("path", relativePath);
                    payload.put("type", "directory");
                    ArrayNode arr = payload.putArray("entries");
                    Path dir = Files.exists(target) && Files.isDirectory(target) ? target : workspaceRoot;
                    try (Stream<Path> list = Files.list(dir)) {
                        for (Path p : list.sorted().collect(Collectors.toList())) {
                            ObjectNode entry = arr.addObject();
                            entry.put("name", p.getFileName().toString());
                            entry.put("isDirectory", Files.isDirectory(p));
                            entry.put("size", Files.size(p));
                        }
                    }
                    return ok(request, payload);
                }
                default -> {
                    return error(request, "unsupported_action", "Unsupported file action: " + action);
                }
            }
        } catch (IOException e) {
            log.error("File operation error: {} {}", action, target, e);
            return error(request, "io_error", e.getMessage());
        }
    }

    private Path resolveSafe(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return workspaceRoot;
        }
        // Strip leading slashes and .. traversal
        String normalized = relativePath.replace("\\", "/")
            .replaceAll("^(\\./)+", "")
            .replaceAll("/+", "/")
            .replaceAll("^/+", "");
        if (normalized.contains("..")) {
            return null;
        }
        Path resolved = workspaceRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            return null;
        }
        return resolved;
    }

    private Mono<GatewayFrame> ok(GatewayFrame req, ObjectNode payload) {
        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(req.getId())
            .ok(true)
            .payload(payload)
            .build());
    }

    private Mono<GatewayFrame> error(GatewayFrame req, String code, String message) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("errorCode", code);
        payload.put("errorMessage", message);
        return Mono.just(GatewayFrame.builder()
            .type("res")
            .id(req.getId())
            .ok(false)
            .payload(payload)
            .build());
    }
}
