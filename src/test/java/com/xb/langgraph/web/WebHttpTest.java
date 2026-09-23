package com.xb.langgraph.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebHttpTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30));
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
    JsonNode run(String demo, String input) throws Exception {
        var response = request("POST", "/api/demos/" + demo + "/run", input);
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }
    JsonNode resume(String id, boolean approved) throws Exception {
        var response = request("POST", "/api/executions/" + id + "/resume", "{\"approved\":" + approved + "}");
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }

    @Test void catalogContains18LessonsAndMermaid() throws Exception {
        var response = request("GET", "/api/demos", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response)).hasSize(18);
        assertThat(json(response).get(0).path("id").asText()).isEqualTo("demo01");
        assertThat(json(response).get(0).has("sampleInput")).isTrue();
        var graph = request("GET", "/api/demos/demo01/graph", null);
        assertThat(graph.statusCode()).isEqualTo(200);
        assertThat(graph.body()).contains("flowchart TD", "__START__", "__END__");
    }

    @Test void everyLessonRunsAndRendersWithItsNativeConfiguration() throws Exception {
        for (int lesson = 1; lesson <= 18; lesson++) {
            var demo = "demo%02d".formatted(lesson);
            var graph = request("GET", "/api/demos/" + demo + "/graph", null);
            assertThat(graph.statusCode()).as(demo).isEqualTo(200);
            assertThat(graph.body()).contains("flowchart TD");
            var result = run(demo, "{\"input\":{}}");
            for (int review = 0; review < 2 && result.path("status").asText().equals("WAITING_REVIEW"); review++)
                result = resume(result.path("executionId").asText(), true);
            assertThat(result.path("status").asText()).as(demo).isEqualTo("COMPLETED");
            assertThat(result.path("visitedNodes").get(0).asText()).isEqualTo("__START__");
        }
    }

    @Test void unknownResourcesAre404() throws Exception {
        for (var path : List.of("/api/demos/missing/graph", "/api/demos/missing/stream", "/api/executions/missing", "/api/executions/missing/history"))
            assertThat(request("GET", path, null).statusCode()).isEqualTo(404);
        assertThat(request("POST", "/api/demos/missing/run", "{\"input\":{}}").statusCode()).isEqualTo(404);
    }

    @Test void inputBoundaryRejectsUnknownNullAndWrongScalarTypes() throws Exception {
        for (var body : List.of("null", "[]", "{\"input\":null}", "{\"oops\":1}", "{\"input\":{\"approved\":true}}", "{\"input\":{\"name\":null}}", "{\"input\":{\"name\":5}}", "{\"input\":{\"name\":[]}}"))
            assertThat(request("POST", "/api/demos/demo01/run", body).statusCode()).as(body).isEqualTo(400);
        assertThat(request("POST", "/api/demos/demo17/run", "{\"input\":{\"amount\":1.5}}").statusCode()).isEqualTo(400);
    }

    @Test void happyRunAndHistoryAreIndependentAndChronological() throws Exception {
        var first = run("demo01", "{\"input\":{\"name\":\"Alice\"}}");
        var second = run("demo01", "{\"input\":{\"name\":\"Bob\"}}");
        assertThat(first.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(first.path("state").toString()).contains("Alice").doesNotContain("Bob");
        assertThat(second.path("executionId")).isNotEqualTo(first.path("executionId"));
        var id = first.path("executionId").asText();
        assertThat(json(request("GET", "/api/executions/" + id, null))).isEqualTo(first);
        var history = json(request("GET", "/api/executions/" + id + "/history", null));
        assertThat(history.size()).isGreaterThanOrEqualTo(2);
        assertThat(history.get(0).path("node").asText()).isEqualTo("__START__");
        assertThat(history.toString()).contains("Alice").doesNotContain("Bob");
    }

    @Test void humanReviewRejectAndApproveAndStrictResume() throws Exception {
        for (boolean approved : List.of(false, true)) {
            var waiting = run("demo10", "{\"input\":{}}");
            assertThat(waiting.path("status").asText()).isEqualTo("WAITING_REVIEW");
            assertThat(waiting.path("pendingNode").asText()).isEqualTo("review");
            var id = waiting.path("executionId").asText();
            for (var invalid : List.of("null", "{}", "{\"approved\":null}", "{\"approved\":\"true\"}", "{\"approved\":1}", "{\"approved\":true,\"state\":{}}"))
                assertThat(request("POST", "/api/executions/" + id + "/resume", invalid).statusCode()).as(invalid).isEqualTo(400);
            var done = resume(id, approved);
            assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(done.path("state").path("status").asText()).isEqualTo(approved ? "approved" : "rejected");
            assertThat(request("POST", "/api/executions/" + id + "/resume", "{\"approved\":true}").statusCode()).isEqualTo(409);
        }
    }

    @Test void expenseUsesTwoFreshConsecutiveCheckpoints() throws Exception {
        var first = run("demo17", "{\"input\":{\"amount\":1501}}");
        var id = first.path("executionId").asText();
        assertThat(first.path("pendingNode").asText()).isEqualTo("managerReview");
        var second = resume(id, true);
        assertThat(second.path("status").asText()).isEqualTo("WAITING_REVIEW");
        assertThat(second.path("pendingNode").asText()).isEqualTo("financeReview");
        assertThat(second.path("state").has("financeApproved")).isFalse();
        var done = resume(id, true);
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.path("state").path("paid").asBoolean()).isTrue();
        assertThat(done.path("visitedNodes").toString()).contains("managerReview", "financeReview", "pay");
    }

    @Test void modelMessagesSerializeInSseAndNativeHistory() throws Exception {
        var response = request("GET", "/api/demos/demo12/stream", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("event:completed").doesNotContain("event:error");
        var data = response.body().lines().filter(line -> line.startsWith("data:")).toList();
        var result = mapper.readTree(data.getLast().substring(5));
        assertThat(result.path("state").path("answer").asText()).contains("42");
        assertThat(result.path("state").path("messages")).hasSize(4);
        var history = request("GET", "/api/executions/" + result.path("executionId").asText() + "/history", null);
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(json(history).get(0).path("state").path("messages")).hasSize(0);
        assertThat(json(history).get(json(history).size()-1).path("state").path("messages")).hasSize(4);
    }

    @Test void expenseThresholdAndFinanceRejectionUseOnlyPendingDecision() throws Exception {
        var small = run("demo17", "{\"input\":{\"amount\":1000}}");
        var paid = resume(small.path("executionId").asText(),true);
        assertThat(paid.path("state").path("paid").asBoolean()).isTrue();
        assertThat(paid.path("visitedNodes").toString()).doesNotContain("financeReview");
        var large = run("demo17", "{\"input\":{\"amount\":1001}}");
        var id = large.path("executionId").asText();
        assertThat(resume(id,true).path("pendingNode").asText()).isEqualTo("financeReview");
        var rejected = resume(id,false);
        assertThat(rejected.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(rejected.path("state").path("paid").asBoolean()).isFalse();
        assertThat(rejected.path("state").path("managerApproved").asBoolean()).isTrue();
        assertThat(rejected.path("state").path("financeApproved").asBoolean()).isFalse();
        assertThat(rejected.path("state").has("approved")).isFalse();
    }

    @Test void sseHasOrderedLifecycleAndPersistsTerminalStatus() throws Exception {
        var response = request("GET", "/api/demos/demo01/stream", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");
        assertThat(response.body()).contains("event:started", "event:node", "event:completed").doesNotContain("event:error");
        assertThat(response.body().indexOf("event:started")).isLessThan(response.body().indexOf("event:node"));
        assertThat(response.body().lastIndexOf("event:node")).isLessThan(response.body().indexOf("event:completed"));
        var data = response.body().lines().filter(line -> line.startsWith("data:")).toList();
        var terminal = mapper.readTree(data.getLast().substring(5));
        assertThat(terminal.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(json(request("GET", "/api/executions/" + terminal.path("executionId").asText(), null)).path("status").asText()).isEqualTo("COMPLETED");
        var waiting = request("GET", "/api/demos/demo10/stream", null);
        assertThat(waiting.body()).contains("event:waiting", "WAITING_REVIEW").doesNotContain("event:completed");
        var waitingData = waiting.body().lines().filter(line -> line.startsWith("data:")).toList();
        resume(mapper.readTree(waitingData.getLast().substring(5)).path("executionId").asText(), false);
    }
}
