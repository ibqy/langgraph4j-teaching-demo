package com.xb.langgraph.web;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.concurrent.*;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "demo.web.stream-timeout-ms=500")
@Import(WebLifecycleTest.Fixtures.class)
class WebLifecycleTest extends WebHttpTest {
    // Keep this context's larger test catalog separate from the 18-lesson contract.
    @Override @Test void catalogContains18LessonsAndMermaid() throws Exception {
        assertThat(json(request("GET", "/api/demos", null)).size()).isEqualTo(23);
    }

    @Test void terminalOnlyEvictionAndActiveCapacityBoundary() throws Exception {
        var terminal = run("demo01", "{\"input\":{}}").path("executionId").asText();
        var ids = new java.util.ArrayList<String>();
        try {
            for (int i = 0; i < 100; i++) ids.add(run("demo10", "{\"input\":{}}").path("executionId").asText());
            assertThat(request("GET", "/api/executions/" + terminal, null).statusCode()).isEqualTo(404);
            assertThat(request("POST", "/api/demos/demo01/run", "{\"input\":{}}").statusCode()).isEqualTo(503);
            assertThat(request("GET", "/api/demos/demo01/stream", null).statusCode()).isEqualTo(503);
            assertThat(request("GET", "/api/executions/" + ids.getFirst(), null).statusCode()).isEqualTo(200);
            resume(ids.removeFirst(), false);
            assertThat(run("demo01", "{\"input\":{}}").path("status").asText()).isEqualTo("COMPLETED");
        } finally { for (var id : ids) resume(id, false); }
    }

    @Test void failuresAndRecursionNeverReportSuccessAndErrorsAreSanitized() throws Exception {
        for (var demo : java.util.List.of("testFail", "testLoop", "testChildFail")) {
            var result = run(demo, "{\"input\":{}}");
            assertThat(result.path("status").asText()).as(demo).isEqualTo("FAILED");
            assertThat(result.toString()).doesNotContain("secret-token", "java.lang", "stackTrace");
            assertThat(result.path("error").asText()).isNotBlank();
            var stream = request("GET", "/api/demos/" + demo + "/stream", null);
            assertThat(stream.body()).contains("event:started", "event:error", "FAILED").doesNotContain("event:completed", "secret-token");
        }
        assertThat(run("testChild", "{\"input\":{}}").path("state").path("parentDone").asBoolean()).isTrue();
    }

    @Test void runningExecutionRejectsResumeAndHistoryWithoutBlockingOtherExecutions() throws Exception {
        Fixtures.entered = new CountDownLatch(1);
        Fixtures.release = new CountDownLatch(1);
        Fixtures.stopped = new CountDownLatch(1);
        var client = HttpClient.newHttpClient();
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/demos/testSlow/stream")).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body()))) {
            String line; String id = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) { id = mapper.readTree(line.substring(5)).path("executionId").asText(); break; }
            }
            assertThat(id).isNotBlank();
            assertThat(Fixtures.entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(json(request("GET", "/api/executions/" + id, null)).path("status").asText()).isEqualTo("RUNNING");
            assertThat(request("GET", "/api/executions/" + id + "/history", null).statusCode()).isEqualTo(409);
            assertThat(request("POST", "/api/executions/" + id + "/resume", "{\"approved\":true}").statusCode()).isEqualTo(409);
            assertThat(run("demo01", "{\"input\":{}}").path("status").asText()).isEqualTo("COMPLETED");
            final String executionId = id;
            await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                    assertThat(json(request("GET", "/api/executions/" + executionId, null)).path("status").asText()).isEqualTo("FAILED"));
            assertThat(Fixtures.stopped.await(2, TimeUnit.SECONDS)).as("timeout interrupts the running node, not only its consumer").isTrue();
            var ending = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            assertThat(ending).contains("event:error", "Execution cancelled").doesNotContain("event:completed");
            assertThat(ending.split("event:error", -1)).hasSize(2);
        } finally { Fixtures.release.countDown(); }
    }

    @Test void disconnectedSseClientCannotLeaveAnExecutionRunning() throws Exception {
        Fixtures.entered = new CountDownLatch(1);
        Fixtures.release = new CountDownLatch(1);
        Fixtures.stopped = new CountDownLatch(1);
        String id = null;
        try {
            var client = HttpClient.newHttpClient();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/demos/testSlow/stream")).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) { id = mapper.readTree(line.substring(5)).path("executionId").asText(); break; }
                }
                assertThat(Fixtures.entered.await(5,TimeUnit.SECONDS)).isTrue();
            }
            assertThat(id).isNotBlank();
            final String executionId = id;
            await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                    assertThat(json(request("GET", "/api/executions/" + executionId, null)).path("status").asText()).isEqualTo("FAILED"));
            assertThat(Fixtures.stopped.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(request("POST", "/api/executions/" + id + "/resume", "{\"approved\":true}").statusCode()).isEqualTo(409);
        } finally { Fixtures.release.countDown(); }
    }

    @TestConfiguration
    static class Fixtures {
        static volatile CountDownLatch entered = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(1);
        static volatile CountDownLatch stopped = new CountDownLatch(1);
        @Bean GraphDemo failing() { return demo("testFail", () -> simple().addNode("fail", node_async(s -> { throw new IllegalStateException("secret-token"); })).addEdge(StateGraph.START,"fail").addEdge("fail",StateGraph.END)); }
        @Bean GraphDemo looping() { return demo("testLoop", () -> simple().addNode("loop", node_async(s -> Map.of("sum",1))).addEdge(StateGraph.START,"loop").addEdge("loop","loop")); }
        @Bean GraphDemo child() { return withChild("testChild", false); }
        @Bean GraphDemo childFail() { return withChild("testChildFail", true); }
        @Bean GraphDemo slow() { return demo("testSlow", () -> simple().addNode("slow", node_async(s -> {
            entered.countDown();
            try { release.await(20, TimeUnit.SECONDS); return Map.of("done",true); }
            finally { stopped.countDown(); }
        })).addEdge(StateGraph.START,"slow").addEdge("slow",StateGraph.END)); }
        static StateGraph<LearningState> simple() { return new StateGraph<>(LearningState.SCHEMA,LearningState::new); }
        static GraphDemo withChild(String id, boolean fail) {
            return demo(id, () -> {
                var child = simple().addNode("inner",node_async(s -> Map.of("childDone",true)))
                        .addEdge(StateGraph.START,"inner").addEdge("inner",StateGraph.END).compile();
                return simple().addNode("child",child).addNode("after",node_async(s -> {
                    if (fail) throw new IllegalStateException("secret-token");
                    return Map.of("parentDone",true);
                })).addEdge(StateGraph.START,"child").addEdge("child","after").addEdge("after",StateGraph.END);
            });
        }
        interface Factory { StateGraph<LearningState> create() throws GraphStateException; }
        static GraphDemo demo(String id, Factory factory) {
            return new GraphDemo() {
                public String id() { return id; }
                public String title() { return id; }
                public String level() { return "test"; }
                public Map<String,Object> sampleInput() { return Map.of(); }
                public StateGraph<LearningState> graph() throws GraphStateException { return factory.create(); }
            };
        }
    }
}
