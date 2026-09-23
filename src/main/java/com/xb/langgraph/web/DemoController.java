package com.xb.langgraph.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class DemoController {
    private final DemoRuntime runtime;
    public DemoController(DemoRuntime runtime) { this.runtime = runtime; }

    @GetMapping("/demos")
    public List<DemoRuntime.Lesson> demos() { return runtime.catalog(); }

    @GetMapping(value = "/demos/{id}/graph", produces = MediaType.TEXT_PLAIN_VALUE)
    public String graph(@PathVariable String id) { return runtime.graph(id); }

    @PostMapping("/demos/{id}/run")
    public DemoRuntime.Result run(@PathVariable String id, @RequestBody JsonNode body) {
        var sample = runtime.demo(id).sampleInput();
        requireObject(body);
        requireKeys(body, Set.of("input"));
        var input = new LinkedHashMap<>(sample);
        if (body.has("input")) {
            var overrides = body.get("input");
            requireObject(overrides);
            requireKeys(overrides, sample.keySet());
            overrides.properties().forEach(entry -> input.put(entry.getKey(), scalar(entry.getValue(), sample.get(entry.getKey()))));
        }
        return runtime.run(id, input);
    }

    @GetMapping("/executions/{id}")
    public DemoRuntime.Result execution(@PathVariable String id) { return runtime.get(id); }

    @GetMapping("/executions/{id}/history")
    public List<DemoRuntime.History> history(@PathVariable String id) { return runtime.history(id); }

    @PostMapping("/executions/{id}/resume")
    public DemoRuntime.Result resume(@PathVariable String id, @RequestBody JsonNode body) {
        runtime.get(id);
        requireObject(body);
        requireKeys(body, Set.of("approved"));
        if (!body.has("approved") || !body.get("approved").isBoolean()) throw invalid();
        return runtime.resume(id, body.get("approved").booleanValue());
    }

    @GetMapping(value = "/demos/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) { return runtime.stream(id); }

    private static void requireObject(JsonNode node) { if (node == null || !node.isObject()) throw invalid(); }
    private static void requireKeys(JsonNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw invalid(); });
    }
    private static Object scalar(JsonNode node, Object sample) {
        if (sample instanceof String && node.isTextual() && !node.textValue().isBlank() && node.textValue().length() <= 2000) return node.textValue();
        if (sample instanceof Integer && node.isIntegralNumber() && node.canConvertToInt()) return node.intValue();
        if (sample instanceof Long && node.isIntegralNumber() && node.canConvertToLong()) return node.longValue();
        if (sample instanceof Boolean && node.isBoolean()) return node.booleanValue();
        if (sample instanceof Double && node.isNumber() && Double.isFinite(node.doubleValue())) return node.doubleValue();
        throw invalid();
    }
    private static ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, "Invalid request fields or scalar types"); }
}
