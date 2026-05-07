package com.myclaw.ai.runtime;

import com.myclaw.ai.provider.ChatCompletion;
import com.myclaw.ai.provider.ModelProvider;
import com.myclaw.ai.provider.ModelProviderRegistry;
import com.myclaw.ai.provider.StreamChunk;
import com.myclaw.ai.tool.Tool;
import com.myclaw.ai.tool.ToolRegistry;
import com.myclaw.core.config.ProviderConfig;
import com.myclaw.core.model.AgentContext;
import com.myclaw.core.model.Session;
import com.myclaw.core.protocol.ChatMessage;
import com.myclaw.core.store.SessionStore;
import com.myclaw.core.tool.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLoopService {

    private final ModelProviderRegistry providerRegistry;
    private final SessionStore sessionStore;
    private final ToolRegistry toolRegistry;
    private final ConcurrentHashMap<String, SessionLane> lanes = new ConcurrentHashMap<>();

    public Flux<AgentStreamEvent> run(String runId, AgentContext agent, Session session, String userMessage, String modelRef, ProviderConfig providerConfig) {
        Sinks.Many<AgentStreamEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        String laneKey = session.getSessionKey();

        SessionLane lane = lanes.computeIfAbsent(laneKey, k -> new SessionLane());
        lane.submit(() -> executeLoop(runId, agent, session, userMessage, modelRef, providerConfig, sink));

        return sink.asFlux()
            .doOnTerminate(() -> {
                if (lane.isEmpty()) {
                    lanes.remove(laneKey);
                }
            });
    }

    private void executeLoop(String runId, AgentContext agent, Session session,
                             String userMessage, String modelRef, ProviderConfig providerConfig,
                             Sinks.Many<AgentStreamEvent> sink) {
        try {
            sink.tryEmitNext(AgentStreamEvent.lifecycle(runId, "start", null));

            // Add user message
            ChatMessage userMsg = ChatMessage.builder()
                .role("user")
                .content(userMessage)
                .timestamp(Instant.now().toString())
                .runId(runId)
                .build();
            session.getMessages().add(userMsg);
            session.setLastInteractionAt(Instant.now());
            if (sessionStore != null) {
                sessionStore.save(session);
            }

            // Build message history for LLM (last 20 messages)
            List<ChatMessage> history = new ArrayList<>(session.getMessages());
            if (history.size() > 20) {
                history = history.subList(history.size() - 20, history.size());
            }

            String systemPrompt = buildSystemPrompt(agent);
            String apiFormat = providerConfig != null && providerConfig.getApiFormat() != null
                ? providerConfig.getApiFormat() : "openai";
            log.info("Agent runId={} resolving provider for apiFormat={}, model={}, baseUrl={}",
                     runId, apiFormat, modelRef, providerConfig != null ? providerConfig.getBaseUrl() : "null");
            ModelProvider provider = providerRegistry.resolve(modelRef, apiFormat);
            if (provider == null) {
                sink.tryEmitNext(AgentStreamEvent.error(runId, "No provider available for apiFormat: " + apiFormat + ", model: " + modelRef));
                sink.tryEmitNext(AgentStreamEvent.lifecycle(runId, "error", null));
                sink.tryEmitComplete();
                return;
            }
            log.info("Agent runId={} using provider={}, historySize={}", runId, provider.getProviderId(), history.size());

            List<ToolDefinition> availableTools = toolRegistry != null ? toolRegistry.listDefinitions() : List.of();
            if (!availableTools.isEmpty()) {
                runWithTools(runId, session, modelRef, providerConfig, systemPrompt, provider, availableTools, sink);
            } else {
                runStreaming(runId, session, modelRef, providerConfig, systemPrompt, provider, sink);
            }

        } catch (Exception e) {
            log.error("Unexpected agent loop error", e);
            sink.tryEmitNext(AgentStreamEvent.error(runId, e.getMessage()));
            sink.tryEmitNext(AgentStreamEvent.lifecycle(runId, "error", null));
            sink.tryEmitComplete();
        }
    }

    private void runStreaming(String runId, Session session, String modelRef, ProviderConfig providerConfig,
                              String systemPrompt, ModelProvider provider, Sinks.Many<AgentStreamEvent> sink) {
        List<ChatMessage> history = new ArrayList<>(session.getMessages());
        if (history.size() > 20) {
            history = history.subList(history.size() - 20, history.size());
        }

        StringBuilder assistantContent = new StringBuilder();
        final boolean[] finished = { false };

        provider.streamChat(modelRef, history, systemPrompt, providerConfig)
            .publishOn(Schedulers.boundedElastic())
            .doOnNext(chunk -> {
                log.info("Agent runId={} chunk type={}", runId, chunk.getType());
                switch (chunk.getType()) {
                    case CONTENT -> {
                        assistantContent.append(chunk.getContent());
                        sink.tryEmitNext(AgentStreamEvent.assistant(runId, chunk.getContent()));
                    }
                    case FINISH -> {
                        if (finished[0]) return;
                        finished[0] = true;
                        ChatMessage assistantMsg = ChatMessage.builder()
                            .role("assistant")
                            .content(assistantContent.toString())
                            .timestamp(Instant.now().toString())
                            .runId(runId)
                            .build();
                        session.getMessages().add(assistantMsg);
                        if (sessionStore != null) {
                            sessionStore.save(session);
                        }
                        sink.tryEmitNext(AgentStreamEvent.lifecycle(runId, "end", assistantContent.toString()));
                    }
                    case ERROR -> {
                        sink.tryEmitNext(AgentStreamEvent.error(runId, chunk.getErrorMessage()));
                        sink.tryEmitNext(AgentStreamEvent.lifecycle(runId, "error", null));
                    }
                }
            })
            .doOnComplete(sink::tryEmitComplete)
            .doOnError(err -> {
                log.error("Agent loop error", err);
                sink.tryEmitNext(AgentStreamEvent.error(runId, err.getMessage()));
                sink.tryEmitNext(AgentStreamEvent.lifecycle(runId, "error", null));
                sink.tryEmitComplete();
            })
            .subscribe();
    }

    private void runWithTools(String runId, Session session, String modelRef, ProviderConfig providerConfig,
                              String systemPrompt, ModelProvider provider, List<ToolDefinition> tools,
                              Sinks.Many<AgentStreamEvent> sink) {
        int maxRounds = 5;
        String finalContent = null;

        for (int round = 0; round < maxRounds; round++) {
            List<ChatMessage> history = new ArrayList<>(session.getMessages());
            if (history.size() > 20) {
                history = history.subList(history.size() - 20, history.size());
            }

            log.info("Agent runId={} tool round={}", runId, round);
            ChatCompletion completion = provider.chatComplete(modelRef, history, systemPrompt, providerConfig, tools);

            if (completion.getToolCalls() == null || completion.getToolCalls().isEmpty()) {
                finalContent = completion.getContent();
                break;
            }

            // Add assistant message with tool calls
            ChatMessage assistantMsg = ChatMessage.builder()
                .role("assistant")
                .content(completion.getContent() != null ? completion.getContent() : "")
                .build();
            session.getMessages().add(assistantMsg);

            // Execute tools
            for (com.myclaw.core.tool.ToolCall tc : completion.getToolCalls()) {
                Tool tool = toolRegistry.get(tc.getName());
                String result = tool != null ? tool.execute(tc.getArguments()) : "Error: tool not found: " + tc.getName();
                ChatMessage toolResult = ChatMessage.builder()
                    .role("tool")
                    .content(result)
                    .toolCallId(tc.getId())
                    .build();
                session.getMessages().add(toolResult);
            }

            if (sessionStore != null) {
                sessionStore.save(session);
            }
        }

        if (finalContent != null && !finalContent.isEmpty()) {
            sink.tryEmitNext(AgentStreamEvent.assistant(runId, finalContent));
            ChatMessage assistantMsg = ChatMessage.builder()
                .role("assistant")
                .content(finalContent)
                .timestamp(Instant.now().toString())
                .runId(runId)
                .build();
            session.getMessages().add(assistantMsg);
            if (sessionStore != null) {
                sessionStore.save(session);
            }
        }
        sink.tryEmitNext(AgentStreamEvent.lifecycle(runId, "end", finalContent));
        sink.tryEmitComplete();
    }

    private String buildSystemPrompt(AgentContext agent) {
        String now = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"))
            .format(Instant.now());
        return "You are " + agent.getName() + ", a helpful AI assistant.\n" +
               "Agent ID: " + agent.getAgentId() + "\n" +
               "Current date and time: " + now + " (Asia/Shanghai)\n" +
               "Be concise and helpful.";
    }

    // Per-session serial execution lane
    private static class SessionLane {
        private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "agent-lane");
            t.setDaemon(true);
            return t;
        });

        SessionLane() {
            executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Runnable task = queue.take();
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        void submit(Runnable task) {
            queue.offer(task);
        }

        boolean isEmpty() {
            return queue.isEmpty();
        }
    }
}
