package com.xb.langgraph.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xb.langgraph.*;
import com.xb.langgraph.demo17.ExpenseApprovalDemo;
import com.xb.langgraph.demo06.ParallelFanoutDemo;
import org.bsc.langgraph4j.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

class DemoRuntimeTest {
    @ParameterizedTest
    @ValueSource(strings = {"completionCallback", "timeoutCallback", "errorCallback"})
    void delayedSpringCallbackCannotCancelResumedReview(String callback) throws Exception {
        var block = new BlockedWork(false);
        var runtime = new DemoRuntime(List.of(reviewDemo(block)), new ObjectMapper(), 60_000);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var emitter = runtime.stream("reviewTest");
            var execution = onlyExecution(runtime);
            joinWorker(execution);
            var id = result(execution).executionId();
            assertThat(runtime.get(id).status()).isEqualTo(DemoRuntime.Status.WAITING_REVIEW);
            var resumed = executor.submit(() -> runtime.resume(id, true));
            try {
                assertThat(block.entered.await(5, TimeUnit.SECONDS)).isTrue();
                // Invoke Spring's actual callback object, delayed until the next generation runs.
                springCallback(emitter, callback).run();
                assertThat(runtime.get(id).status()).isEqualTo(DemoRuntime.Status.RUNNING);
            } finally { block.release.countDown(); }
            assertThat(resumed.get(5, TimeUnit.SECONDS).status()).isEqualTo(DemoRuntime.Status.COMPLETED);
        } finally { block.release.countDown(); runtime.shutdown(); }
    }

    @Test void alreadyFiredDeadlineMustRecheckFinishedAfterAcquiringSessionMonitor() throws Exception {
        var block = new BlockedWork(false);
        var demo = WebLifecycleTest.Fixtures.demo("blocked", () -> WebLifecycleTest.Fixtures.simple()
                .addNode("work", node_async(s -> Map.of("done", block.run("work"))))
                .addEdge(StateGraph.START, "work").addEdge("work", StateGraph.END));
        var runtime = new DemoRuntime(List.of(demo), new ObjectMapper(), 60_000);
        try {
            var emitter = runtime.stream(demo.id());
            var execution = onlyExecution(runtime);
            assertThat(block.entered.await(5, TimeUnit.SECONDS)).isTrue();
            // The application deadline and Spring timeout use this same registered Runnable.
            var delegates = (List<?>) ReflectionTestUtils.getField(
                    ReflectionTestUtils.getField(emitter, "timeoutCallback"), "delegates");
            var deadline = (Runnable) delegates.getFirst();
            Object session = null;
            for (var field : deadline.getClass().getDeclaredFields()) {
                if (field.getType().getSimpleName().equals("StreamSession")) {
                    field.setAccessible(true);
                    session = field.get(deadline);
                }
            }
            assertThat(session).isNotNull();
            Thread fired;
            synchronized (session) {
                fired = Thread.ofPlatform().daemon().start(deadline);
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5))
                        .until(() -> fired.getState() == Thread.State.BLOCKED);
                // Isolate the finish/expired-deadline interleaving under the actual session monitor.
                ReflectionTestUtils.invokeMethod(session, "finish", result(execution));
            }
            fired.join(5_000);
            assertThat(fired.isAlive()).isFalse();
            assertThat(result(execution).status()).isEqualTo(DemoRuntime.Status.RUNNING);
            block.release.countDown();
            joinWorker(execution);
            assertThat(result(execution).status()).isEqualTo(DemoRuntime.Status.COMPLETED);
        } finally { block.release.countDown(); runtime.shutdown(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"deadline", "completionCallback", "timeoutCallback", "errorCallback"})
    void deadlineInterruptsRealNativeParallelSource(String callback) throws Exception {
        var block = new BlockedWork(false);
        var demo = new ParallelFanoutDemo(block::run, topic -> "right") {
            @Override public RunnableConfig config(String threadId) {
                return RunnableConfig.builder(super.config(threadId)).addMetadata("demoMarker", "preserved").build();
            }
        };
        var runtime = new DemoRuntime(List.of(demo), new ObjectMapper(), callback.equals("deadline") ? 500 : 60_000);
        try {
            var emitter = runtime.stream("demo06");
            var execution = onlyExecution(runtime);
            assertThat(block.entered.await(5, TimeUnit.SECONDS)).isTrue();
            if (!callback.equals("deadline")) springCallback(emitter, callback).run();
            joinWorker(execution);
            assertThat(result(execution).status()).isEqualTo(DemoRuntime.Status.FAILED);
            assertThat(block.stopped.await(2, TimeUnit.SECONDS))
                    .as("native branch source is interrupted, not just the stream consumer").isTrue();
            var config = (RunnableConfig) ReflectionTestUtils.getField(execution, "baseConfig");
            assertThat(config.metadata("demoMarker")).contains("preserved");
            var executor = (Executor) config.metadata(
                    org.bsc.langgraph4j.internal.node.ParallelNode.formatNodeId("dispatch")).orElseThrow();
            var calls = new AtomicInteger();
            assertThatThrownBy(() -> executor.execute(calls::incrementAndGet))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(calls).hasValue(0);
        } finally { block.release.countDown(); runtime.shutdown(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deadlineInterruptsNestedChildNode(boolean compiled) throws Exception {
        var block = new BlockedWork(false);
        var demo = WebLifecycleTest.Fixtures.demo("childBlock", () -> {
            var child = WebLifecycleTest.Fixtures.simple()
                    .addNode("inner", node_async(s -> Map.of("done", block.run("child"))))
                    .addEdge(StateGraph.START, "inner").addEdge("inner", StateGraph.END);
            var parent = WebLifecycleTest.Fixtures.simple();
            if (compiled) parent.addNode("child", child.compile());
            else parent.addNode("child", child);
            // Include another compiled level so ownership must recurse.
            parent.addEdge(StateGraph.START, "child").addEdge("child", StateGraph.END);
            return WebLifecycleTest.Fixtures.simple().addNode("outer", parent.compile())
                    .addEdge(StateGraph.START, "outer").addEdge("outer", StateGraph.END);
        });
        var runtime = new DemoRuntime(List.of(demo), new ObjectMapper(), 500);
        try {
            runtime.stream(demo.id());
            var execution = onlyExecution(runtime);
            assertThat(block.entered.await(5, TimeUnit.SECONDS)).isTrue();
            joinWorker(execution);
            assertThat(result(execution).status()).isEqualTo(DemoRuntime.Status.FAILED);
            assertThat(block.stopped.await(2, TimeUnit.SECONDS)).as("child action is interrupted").isTrue();
        } finally { block.release.countDown(); runtime.shutdown(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelledParallel", "failedParallel", "cancelledChild"})
    void terminalExecutionRetainsCapacityUntilOwnedWorkActuallyStops(String scenario) throws Exception {
        var block = new BlockedWork(true);
        GraphDemo producer = scenario.equals("cancelledChild")
                ? WebLifecycleTest.Fixtures.demo("childBlock", () -> {
                    var child = WebLifecycleTest.Fixtures.simple()
                            .addNode("inner", node_async(s -> Map.of("done", block.run("child"))))
                            .addEdge(StateGraph.START, "inner").addEdge("inner", StateGraph.END).compile();
                    return WebLifecycleTest.Fixtures.simple().addNode("child", child)
                            .addEdge(StateGraph.START, "child").addEdge("child", StateGraph.END);
                })
                : new ParallelFanoutDemo(block::run, topic -> {
                    if (scenario.equals("failedParallel")) {
                        try { assertThat(block.entered.await(5, TimeUnit.SECONDS)).isTrue(); }
                        catch (InterruptedException interrupted) { throw new RuntimeException(interrupted); }
                        throw new IllegalStateException("branch failure");
                    }
                    return "right";
                });
        var runtime = new DemoRuntime(List.of(producer, reviewDemo(new BlockedWork(false))), new ObjectMapper(), 60_000);
        try {
            var emitter = runtime.stream(producer.id());
            var execution = onlyExecution(runtime);
            var id = result(execution).executionId();
            assertThat(block.entered.await(5, TimeUnit.SECONDS)).isTrue();
            if (!scenario.equals("failedParallel")) springCallback(emitter, "timeoutCallback").run();
            joinWorker(execution);
            assertThat(runtime.get(id).status()).isEqualTo(DemoRuntime.Status.FAILED);
            assertThat(block.stopped.getCount()).isEqualTo(1);
            for (int i = 0; i < 99; i++) runtime.run("reviewTest", Map.of());
            assertThatThrownBy(() -> runtime.run("reviewTest", Map.of()))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
            assertThat(runtime.get(id).status()).isEqualTo(DemoRuntime.Status.FAILED);
            block.release.countDown();
            assertThat(block.stopped.await(5, TimeUnit.SECONDS)).isTrue();
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat((Boolean) ReflectionTestUtils.invokeMethod(execution, "evictable")).isTrue());
            assertThat(runtime.run("reviewTest", Map.of()).status()).isEqualTo(DemoRuntime.Status.WAITING_REVIEW);
            assertThatThrownBy(() -> runtime.get(id)).isInstanceOfSatisfying(ApiException.class,
                    error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        } finally { block.release.countDown(); runtime.shutdown(); }
    }

    private static GraphDemo reviewDemo(BlockedWork block) {
        return new GraphDemo() {
            public String id() { return "reviewTest"; }
            public String title() { return id(); }
            public String level() { return "test"; }
            public Map<String, Object> sampleInput() { return Map.of(); }
            public Set<String> reviewNodes() { return Set.of("review"); }
            public StateGraph<LearningState> graph() throws GraphStateException {
                return WebLifecycleTest.Fixtures.simple()
                        .addNode("prepare", node_async(s -> Map.of()))
                        .addNode("review", node_async(s -> Map.of("accepted", block.run("review"))))
                        .addEdge(StateGraph.START, "prepare").addEdge("prepare", "review")
                        .addEdge("review", StateGraph.END);
            }
        };
    }

    private static final class BlockedWork {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        final boolean ignoreInterrupt;
        BlockedWork(boolean ignoreInterrupt) { this.ignoreInterrupt = ignoreInterrupt; }
        String run(String input) {
            entered.countDown();
            try {
                for (;;) {
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out");
                        return input;
                    } catch (InterruptedException interrupted) {
                        if (!ignoreInterrupt) { Thread.currentThread().interrupt(); throw new CancellationException(); }
                    }
                }
            } finally { stopped.countDown(); }
        }
    }

    private static Object onlyExecution(DemoRuntime runtime) {
        var executions = (Map<?, ?>) ReflectionTestUtils.getField(runtime, "executions");
        synchronized (executions) { return executions.values().iterator().next(); }
    }

    private static DemoRuntime.Result result(Object execution) {
        return (DemoRuntime.Result) ReflectionTestUtils.getField(execution, "result");
    }

    private static void joinWorker(Object execution) throws InterruptedException {
        var worker = (Thread) ReflectionTestUtils.getField(execution, "worker");
        worker.join(5_000);
        assertThat(worker.isAlive()).as("stream consumer terminated").isFalse();
    }

    @SuppressWarnings("unchecked")
    private static Runnable springCallback(SseEmitter emitter, String name) {
        var callback = ReflectionTestUtils.getField(emitter, name);
        return callback instanceof Runnable runnable ? runnable
                : () -> ((java.util.function.Consumer<Throwable>) callback).accept(new java.io.IOException("disconnected"));
    }

    @Test void simultaneousResumeAppliesDecisionOnceAndHistoryDoesNotRace() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var compilations = new AtomicInteger();
        GraphDemo demo = new GraphDemo() {
            public String id() { return "reviewTest"; }
            public String title() { return id(); }
            public String level() { return "test"; }
            public Map<String,Object> sampleInput() { return Map.of(); }
            public Set<String> reviewNodes() { return Set.of("review"); }
            public StateGraph<LearningState> graph() throws GraphStateException {
                compilations.incrementAndGet();
                return new StateGraph<>(LearningState.SCHEMA, LearningState::new)
                        .addNode("prepare",node_async(s -> Map.of("ready",true)))
                        .addNode("review",node_async(s -> {
                            calls.incrementAndGet(); entered.countDown();
                            if (!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out");
                            return Map.of("accepted",s.decision("approved"));
                        })).addEdge(StateGraph.START,"prepare").addEdge("prepare","review").addEdge("review",StateGraph.END);
            }
        };
        var runtime = new DemoRuntime(List.of(demo),new ObjectMapper(),60_000);
        var waiting = runtime.run(demo.id(), Map.of());
        var id = waiting.executionId();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> runtime.resume(id,true));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(runtime.get(id).status()).isEqualTo(DemoRuntime.Status.RUNNING);
                assertThatThrownBy(() -> runtime.resume(id,false)).isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
                assertThatThrownBy(() -> runtime.history(id)).isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
            } finally { release.countDown(); }
            assertThat(first.get(5,TimeUnit.SECONDS).state()).containsEntry("accepted",true);
        }
        assertThat(calls).hasValue(1);
        assertThat(compilations).hasValue(1);
        assertThat(runtime.history(id)).extracting(DemoRuntime.History::node).containsExactly("__START__","prepare","review");
        runtime.run(demo.id(), Map.of());
        assertThat(compilations).hasValue(2);
        runtime.shutdown();
    }

    @Test void consecutiveExpenseDecisionsPreserveEarlierHistoryAndOtherExecution() {
        var runtime = new DemoRuntime(List.of(new ExpenseApprovalDemo()),new ObjectMapper(),60_000);
        var first = runtime.run("demo17", Map.of("amount",1501));
        var other = runtime.run("demo17", Map.of("amount",900));
        var before = runtime.history(first.executionId());
        var second = runtime.resume(first.executionId(),true);
        assertThat(second.pendingNode()).isEqualTo("financeReview");
        assertThat(second.state()).containsEntry("managerApproved",true).doesNotContainKey("financeApproved");
        var done = runtime.resume(first.executionId(),true);
        assertThat(done.state()).containsEntry("managerApproved",true).containsEntry("financeApproved",true).containsEntry("paid",true);
        assertThat(done.visitedNodes()).containsOnlyOnce("managerReview","financeReview","pay");
        var history = runtime.history(first.executionId());
        assertThat(history).extracting(DemoRuntime.History::node).containsExactly("__START__","validate","managerReview","financeReview","pay");
        assertThat(before).hasSize(2);
        assertThat(before.getLast().state()).doesNotContainKey("managerApproved");
        history.getLast().state().put("amount",0);
        assertThat(runtime.history(first.executionId()).getLast().state()).containsEntry("amount",1501);
        assertThat(runtime.get(other.executionId()).state()).containsEntry("amount",900).doesNotContainKey("managerApproved");
        assertThat(runtime.get(other.executionId()).status()).isEqualTo(DemoRuntime.Status.WAITING_REVIEW);
        assertThat(runtime.resume(other.executionId(),false).state()).containsEntry("paid",false);
        runtime.shutdown();
    }
}
