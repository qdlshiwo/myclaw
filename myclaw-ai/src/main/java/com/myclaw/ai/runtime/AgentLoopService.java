package com.myclaw.ai.runtime;

import com.myclaw.ai.provider.ModelProvider;
import com.myclaw.ai.provider.ModelProviderRegistry;
import com.myclaw.ai.provider.StreamChunk;
import com.myclaw.core.config.ProviderConfig;
import com.myclaw.core.model.AgentContext;
import com.myclaw.core.model.Session;
import com.myclaw.core.protocol.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLoopService {

    private final ModelProviderRegistry providerRegistry;
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

            // Build message history for LLM (last 20 messages)
            List<ChatMessage> history = new ArrayList<>(session.getMessages());
            if (history.size() > 20) {
                history = history.subList(history.size() - 20, history.size());
            }

            String systemPrompt = buildSystemPrompt(agent);
            String apiFormat = providerConfig != null && providerConfig.getApiFormat() != null
                ? providerConfig.getApiFormat() : "openai";
            ModelProvider provider = providerRegistry.resolve(modelRef, apiFormat);
            if (provider == null) {
                sink.tryEmitNext(AgentStreamEvent.error(runId, "No provider available for apiFormat: " + apiFormat + ", model: " + modelRef));
                sink.tryEmitNext(AgentStreamEvent.lifecycle(runId, "error", null));
                sink.tryEmitComplete();
                return;
            }

            StringBuilder assistantContent = new StringBuilder();

            provider.streamChat(modelRef, history, systemPrompt, providerConfig)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    switch (chunk.getType()) {
                        case CONTENT -> {
                            assistantContent.append(chunk.getContent());
                            sink.tryEmitNext(AgentStreamEvent.assistant(runId, chunk.getContent()));
                        }
                        case FINISH -> {
                            // Persist assistant message
                            ChatMessage assistantMsg = ChatMessage.builder()
                                .role("assistant")
                                .content(assistantContent.toString())
                                .timestamp(Instant.now().toString())
                                .runId(runId)
                                .build();
                            session.getMessages().add(assistantMsg);
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

        } catch (Exception e) {
            log.error("Unexpected agent loop error", e);
            sink.tryEmitNext(AgentStreamEvent.error(runId, e.getMessage()));
            sink.tryEmitNext(AgentStreamEvent.lifecycle(runId, "error", null));
            sink.tryEmitComplete();
        }
    }

    private String buildSystemPrompt(AgentContext agent) {
        return "You are " + agent.getName() + ", a helpful AI assistant.\n" +
               "Agent ID: " + agent.getAgentId() + "\n" +
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
