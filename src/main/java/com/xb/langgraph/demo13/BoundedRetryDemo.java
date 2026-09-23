package com.xb.langgraph.demo13;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/** 应用层重试，不冒充图引擎自动重试；尝试次数只保存在本次执行状态。 */
@Component
public class BoundedRetryDemo implements GraphDemo {
    private static final int MAX_ATTEMPTS = 3;
    public String id() { return "demo13"; }
    public String title() { return "应用层有界重试"; }
    public String level() { return "高阶"; }
    public Map<String,Object> sampleInput() { return Map.of("failuresBeforeSuccess",2); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("initialize",node_async(s -> { s.integer("failuresBeforeSuccess",0,10); return Map.of("attempts",0); }))
                .addNode("attempt",node_async(s -> {
                    int attempt = s.count("attempts") + 1;
                    try {
                        simulatedOperation(attempt,s.count("failuresBeforeSuccess"));
                        return Map.of("attempts",attempt,"status","succeeded","lastError","","trace",List.of("attempt:" + attempt));
                    } catch (TransientFailure e) {
                        return Map.of("attempts",attempt,"status",attempt < MAX_ATTEMPTS ? "retrying" : "exhausted",
                                "lastError",e.getMessage(),"trace",List.of("attempt:" + attempt));
                    }
                }))
                .addEdge(StateGraph.START,"initialize").addEdge("initialize","attempt")
                .addConditionalEdges("attempt",edge_async(s -> s.text("status").equals("retrying") ? "retry" : "done"),
                        Map.of("retry","attempt","done",StateGraph.END));
    }
    private static void simulatedOperation(int attempt, int failures) {
        if (attempt <= failures) throw new TransientFailure("离线模拟暂时性失败");
    }
    private static class TransientFailure extends RuntimeException {
        TransientFailure(String message) { super(message); }
    }
}
