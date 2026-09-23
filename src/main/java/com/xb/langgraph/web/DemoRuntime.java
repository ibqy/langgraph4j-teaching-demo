package com.xb.langgraph.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonGenerator;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import com.xb.langgraph.GraphDemo;
import com.xb.langgraph.LearningState;
import jakarta.annotation.PreDestroy;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.internal.node.ParallelNode;
import org.bsc.langgraph4j.internal.node.SubStateGraphNode;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DemoRuntime {
    private static final int MAX_EXECUTIONS = 100;
    private static final int RECURSION_LIMIT = 100;
    private final Map<String, GraphDemo> demos;
    // This monitor protects membership only, never graph execution or IO.
    private final Map<String, Execution> executions = new LinkedHashMap<>();
    private final ObjectMapper mapper;
    private final long streamTimeout;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("demo-stream-deadline").factory());

    public DemoRuntime(List<GraphDemo> demos, ObjectMapper mapper,
                       @Value("${demo.web.stream-timeout-ms:60000}") long streamTimeout) {
        var catalog = new TreeMap<String, GraphDemo>();
        for (var demo : demos) {
            if (catalog.putIfAbsent(demo.id(), demo) != null) throw new IllegalStateException("Duplicate demo ID");
        }
        this.demos = Collections.unmodifiableMap(catalog);
        // Convert model messages into JSON DTOs without altering graph state or the app mapper.
        var messages = new SimpleModule().addSerializer(ChatMessage.class, new JsonSerializer<ChatMessage>() {
            @Override public void serialize(ChatMessage value, JsonGenerator output, SerializerProvider provider) throws IOException {
                output.writeTree(mapper.readTree(ChatMessageSerializer.messageToJson(value)));
            }
        });
        this.mapper = mapper.copy().registerModule(messages);
        if (streamTimeout <= 0) throw new IllegalArgumentException("Stream timeout must be positive");
        this.streamTimeout = streamTimeout;
    }

    public enum Status { RUNNING, WAITING_REVIEW, COMPLETED, FAILED }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(String executionId, String demoId, Status status, Map<String, Object> state,
                         List<String> visitedNodes, String pendingNode, String error) { }
    public record Lesson(String id, String title, String level, Map<String, Object> sampleInput,
                         Set<String> reviewNodes) { }
    public record History(String checkpointId, String node, String next, Map<String, Object> state) { }

    public List<Lesson> catalog() {
        return demos.values().stream().map(d -> new Lesson(d.id(), d.title(), d.level(), copy(d.sampleInput()), d.reviewNodes())).toList();
    }

    GraphDemo demo(String id) {
        var demo = demos.get(id);
        if (demo == null) throw new ApiException(HttpStatus.NOT_FOUND, "Demo not found");
        return demo;
    }

    public String graph(String id) {
        var demo = demo(id);
        try { return demo.graph().getGraph(GraphRepresentation.Type.MERMAID).content(); }
        catch (Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Graph rendering failed"); }
    }

    public Result run(String id, Map<String, Object> input) {
        var execution = reserve(demo(id), input);
        execution.lock.lock();
        try { execute(execution, GraphInput.args(input), null, null); return execution.result; }
        finally { execution.lock.unlock(); }
    }

    public Result get(String id) { return execution(id).result; }

    public List<History> history(String id) {
        var e = execution(id);
        if (!e.lock.tryLock()) throw conflict();
        try {
            if (e.result.status() == Status.RUNNING) throw conflict();
            if (e.graph == null) return List.of();
            // Native history is newest first; the HTTP contract is chronological.
            var history = new ArrayList<>(e.graph.getStateHistory(e.baseConfig));
            Collections.reverse(history);
            return history.stream().map(s -> new History(s.config().checkPointId().orElse(null), s.node(), s.next(), copy(s.state().data()))).toList();
        } finally { e.lock.unlock(); }
    }

    public Result resume(String id, boolean approved) {
        var e = execution(id);
        if (!e.lock.tryLock()) throw conflict();
        try {
            if (e.result.status() != Status.WAITING_REVIEW) throw conflict();
            String key = decisionKey(e.result.pendingNode());
            if (key == null || !e.demo.reviewNodes().contains(e.result.pendingNode())) throw conflict();
            publish(e, Status.RUNNING, e.result.state(), null, null);
            execute(e, GraphInput.resume(), Map.of(key, approved), null);
            return e.result;
        } finally { e.lock.unlock(); }
    }

    public SseEmitter stream(String id) {
        var demo = demo(id);
        var e = reserve(demo, copy(demo.sampleInput()));
        // Spring marks an emitter unusable before invoking onTimeout. An application deadline
        // runs first so connected clients can still receive a terminal error event.
        var session = new StreamSession(new SseEmitter(streamTimeout + 1000));
        Runnable timeout = () -> session.cancel(e);
        session.emitter.onTimeout(timeout);
        session.emitter.onError(error -> session.cancel(e));
        session.emitter.onCompletion(timeout);
        var worker = Thread.ofVirtual().name("demo-stream-" + e.id).unstarted(() -> {
            e.lock.lock();
            try {
                session.send("started", e.result);
                session.deadline(deadlines.schedule(timeout, streamTimeout, TimeUnit.MILLISECONDS));
                execute(e, GraphInput.args(e.result.state()), null, session);
                session.finish(e.result);
            } catch (Exception failure) {
                session.cancel(e);
            } finally { e.lock.unlock(); }
        });
        e.worker = worker;
        worker.start();
        return session.emitter;
    }

    private void execute(Execution e, GraphInput input, Map<String, Object> decision, StreamSession session) {
        AsyncGenerator.Cancellable<NodeOutput<LearningState>> generator = null;
        try {
            if (e.cancelled) throw new CancellationException();
            if (e.graph == null) {
                var config = RunnableConfig.builder(e.demo.config(e.id))
                        .threadId(e.id).checkPointId(null).nextNode(null).recursionLimit(RECURSION_LIMIT);
                var definition = e.demo.graph();
                installNodeOwnership(e, definition, Collections.newSetFromMap(new IdentityHashMap<>()));
                e.graph = definition.compile(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver()).releaseThread(false)
                        .interruptBefore(e.demo.reviewNodes().toArray(String[]::new))
                        .recursionLimit(RECURSION_LIMIT).build());
                installParallelOwnership(e, definition, config, "");
                // Compiled reduction also exposes fan-outs rewritten by child flattening.
                installParallelOwnership(e, e.graph, config, "");
                e.baseConfig = config.build();
            }
            RunnableConfig config = e.baseConfig;
            if (decision != null) {
                // Never reuse a checkpoint ID from a previous pause. Keep the demo's executor metadata.
                e.graph.updateState(e.graph.getState(e.baseConfig).config(), decision);
                config = e.graph.getState(e.baseConfig).config();
            }
            generator = e.graph.stream(input, config);
            e.generator = generator;
            if (e.cancelled) { generator.cancel(true); throw new CancellationException(); }
            for (var output : generator) {
                if (e.cancelled) throw new CancellationException();
                e.visited.add(output.node());
                publish(e, Status.RUNNING, output.state().data(), null, null);
                if (session != null) {
                    try {
                        session.send("node", Map.of("executionId", e.id, "demoId", e.demo.id(),
                                "node", output.node(), "state", copy(output.state().data())));
                    } catch (IOException | IllegalStateException disconnected) {
                        session.cancel(e);
                        throw disconnected;
                    }
                }
            }
            if (e.cancelled) throw new CancellationException();
            // END outputs may belong to a child graph. Only the exhausted ROOT generator's
            // GraphResult proves completion; an interruption or truncated/error stream does not.
            var result = GraphResult.from(generator);
            if (result.isInterruptionMetadata()) {
                var snapshot = e.graph.getState(e.baseConfig);
                var pending = snapshot.next();
                if (!e.demo.reviewNodes().contains(pending) || decisionKey(pending) == null)
                    throw new IllegalStateException("Unsupported interruption");
                publish(e, Status.WAITING_REVIEW, snapshot.state().data(), pending, null);
            } else if (result.isStateData()) {
                publish(e, Status.COMPLETED, result.asStateData(), null, null);
            } else {
                throw new IllegalStateException("Graph did not complete");
            }
        } catch (Exception failure) {
            publish(e, Status.FAILED, e.result.state(), null, e.cancelled ? "Execution cancelled" : "Graph execution failed");
        } finally {
            e.generator = null;
            if (generator != null) generator.cancel(true);
        }
    }

    private <S extends AgentState> void installNodeOwnership(Execution e, StateGraph<S> definition,
                                                             Set<StateGraph<?>> installed) {
        if (!installed.add(definition)) return;
        // v1.9 cancels the consumer, not the producer. Compiled children consult their
        // own StateGraph hooks at runtime, so installing only on the root is insufficient.
        definition.addWrapCallNodeHook((node, state, nodeConfig, action) -> {
            var thread = Thread.currentThread();
            synchronized (e) {
                if (e.cancelled) throw new CancellationException();
                e.nodeThreads.merge(thread, 1, Integer::sum);
            }
            try {
                var future = action.apply(state, nodeConfig);
                synchronized (e) {
                    if (e.cancelled) future.cancel(true);
                    else e.actions.add(future);
                }
                return future.whenComplete((value, failure) -> {
                    synchronized (e) { e.actions.remove(future); }
                }).thenApply(value -> {
                    if (e.cancelled) throw new CancellationException();
                    return value;
                });
            } finally {
                synchronized (e) {
                    e.nodeThreads.computeIfPresent(thread, (key, count) -> count == 1 ? null : count - 1);
                }
            }
        });
        definition.reduce((nodes, edges) -> {
            for (var node : nodes.elements) {
                if (node instanceof SubGraphNode<?> child) installNodeOwnership(e, child.subGraph(), installed);
            }
            return null;
        });
    }

    private void installParallelOwnership(Execution e, GraphDefinition<?> definition,
                                          RunnableConfig.Builder config, String prefix) {
        definition.reduce((nodes, edges) -> {
            for (var edge : edges.elements) {
                if (edge.targets().size() > 1) {
                    var source = prefix + edge.sourceId();
                    config.removeMetadata(ParallelNode.formatNodeId(source));
                    config.addParallelNodeExecutor(source, e.ownedExecutor);
                }
            }
            for (var node : nodes.elements) {
                if (node instanceof SubGraphNode<?> child) {
                    // Uncompiled children are flattened with prefixed IDs; compiled ones
                    // retain their own IDs and inherit the execution's runnable metadata.
                    var childPrefix = node instanceof SubStateGraphNode<?> ? prefix + child.id() + "-" : "";
                    installParallelOwnership(e, child.subGraph(), config, childPrefix);
                }
            }
            return null;
        });
    }

    private static String decisionKey(String node) {
        if (node == null) return null;
        return switch (node) {
            case "review" -> "approved";
            case "managerReview" -> "managerApproved";
            case "financeReview" -> "financeApproved";
            default -> null;
        };
    }

    private Execution reserve(GraphDemo demo, Map<String, Object> input) {
        var e = new Execution(demo, copy(input));
        synchronized (executions) {
            if (executions.size() >= MAX_EXECUTIONS) {
                var victim = executions.values().stream().filter(Execution::evictable).findFirst();
                if (victim.isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Execution capacity reached; finish an active execution first");
                executions.remove(victim.get().id);
            }
            executions.put(e.id, e);
        }
        return e;
    }

    private Execution execution(String id) {
        synchronized (executions) {
            var e = executions.get(id);
            if (e == null) throw new ApiException(HttpStatus.NOT_FOUND, "Execution not found");
            return e;
        }
    }

    private void publish(Execution e, Status status, Map<String, Object> state, String pending, String error) {
        var snapshot = copy(state);
        synchronized (e) {
            if (e.cancelled) { status = Status.FAILED; pending = null; error = "Execution cancelled"; }
            e.result = new Result(e.id, e.demo.id(), status, snapshot, List.copyOf(e.visited), pending, error);
        }
    }

    private void cancel(Execution e) {
        List<CompletableFuture<?>> actions;
        synchronized (e) {
            if (e.result.status() != Status.RUNNING) return;
            e.cancelled = true;
            var old = e.result;
            e.result = new Result(e.id, e.demo.id(), Status.FAILED, old.state(), old.visitedNodes(), null, "Execution cancelled");
            e.nodeThreads.keySet().forEach(Thread::interrupt);
            e.ownedThreads.forEach(Thread::interrupt);
            actions = List.copyOf(e.actions);
        }
        // Cancelling futures may synchronously remove them via completion callbacks.
        actions.forEach(future -> future.cancel(true));
        var generator = e.generator;
        if (generator != null) generator.cancel(true);
        var worker = e.worker;
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
    }

    private final class StreamSession {
        final SseEmitter emitter;
        private boolean finished;
        private ScheduledFuture<?> deadline;
        StreamSession(SseEmitter emitter) { this.emitter = emitter; }
        synchronized void cancel(Execution e) {
            // Check inside the finish monitor, including deadlines already fired but blocked
            // here. A finished waiting stream no longer owns cancellation of a later resume.
            if (finished) return;
            DemoRuntime.this.cancel(e);
            finish(e.result);
        }
        synchronized void deadline(ScheduledFuture<?> deadline) {
            this.deadline = deadline;
            if (finished) deadline.cancel(false);
        }
        synchronized void cancelDeadline() { if (deadline != null) deadline.cancel(false); }
        synchronized void send(String event, Object body) throws IOException {
            if (!finished) emitter.send(SseEmitter.event().name(event).data(body));
        }
        synchronized void finish(Result terminal) {
            if (finished) return;
            finished = true;
            cancelDeadline();
            try {
                var event = switch (terminal.status()) {
                    case COMPLETED -> "completed";
                    case WAITING_REVIEW -> "waiting";
                    default -> "error";
                };
                emitter.send(SseEmitter.event().name(event).data(terminal));
            } catch (IOException | IllegalStateException disconnected) {
                // The final state is already stored; a disconnected client cannot receive it.
            } finally { emitter.complete(); }
        }
    }

    private Map<String, Object> copy(Map<String, Object> data) {
        return mapper.convertValue(data, new TypeReference<LinkedHashMap<String, Object>>() { });
    }
    private static ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, "Execution is running or is not waiting for review"); }

    @PreDestroy void shutdown() {
        List<Execution> active;
        synchronized (executions) { active = List.copyOf(executions.values()); }
        active.forEach(this::cancel);
        deadlines.shutdownNow();
    }

    private static final class Execution {
        final String id = UUID.randomUUID().toString();
        final GraphDemo demo;
        final ReentrantLock lock = new ReentrantLock();
        final List<String> visited = new ArrayList<>();
        final Map<Thread, Integer> nodeThreads = new HashMap<>();
        final Set<Thread> ownedThreads = new HashSet<>();
        final Executor ownedExecutor = this::executeOwned;
        final Set<CompletableFuture<?>> actions = new HashSet<>();
        volatile Result result;
        volatile boolean cancelled;
        volatile Thread worker;
        volatile AsyncGenerator.Cancellable<NodeOutput<LearningState>> generator;
        CompiledGraph<LearningState> graph;
        RunnableConfig baseConfig;
        Execution(GraphDemo demo, Map<String, Object> input) {
            this.demo = demo;
            this.result = new Result(id, demo.id(), Status.RUNNING, input, List.of(), null, null);
        }
        synchronized void executeOwned(Runnable command) {
            if (cancelled) throw new RejectedExecutionException("Execution cancelled");
            ownedThreads.removeIf(thread -> !thread.isAlive());
            var thread = Thread.ofVirtual().name("demo-branch-" + id).unstarted(() -> {
                synchronized (this) {
                    if (cancelled) return;
                }
                command.run();
            });
            // Registration and start are atomic with cancellation and eviction. Future
            // cancellation is NOT termination: retain threads until isAlive becomes false.
            ownedThreads.add(thread);
            try { thread.start(); }
            catch (RuntimeException | Error failure) { ownedThreads.remove(thread); throw failure; }
        }
        synchronized boolean evictable() {
            ownedThreads.removeIf(thread -> !thread.isAlive());
            return (result.status() == Status.COMPLETED || result.status() == Status.FAILED)
                    && !lock.isLocked() && nodeThreads.isEmpty() && ownedThreads.isEmpty();
        }
    }
}
