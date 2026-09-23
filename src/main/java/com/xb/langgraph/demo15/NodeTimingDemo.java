package com.xb.langgraph.demo15;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class NodeTimingDemo implements GraphDemo {
    public String id() { return "demo15"; }
    public String title() { return "可观测性：真实节点耗时与轨迹"; }
    public String level() { return "高阶"; }
    public Map<String,Object> sampleInput() { return Map.of("topic","LangGraph4j"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("normalize",node_async(timed("normalize",s -> Map.of("normalized",s.inputText("topic").toUpperCase(Locale.ROOT)))))
                .addNode("finish",node_async(timed("finish",s -> Map.of("result",s.text("normalized")))))
                .addEdge(StateGraph.START,"normalize").addEdge("normalize","finish").addEdge("finish",StateGraph.END);
    }
    private NodeAction<LearningState> timed(String name, NodeAction<LearningState> action) {
        return state -> {
            long started = System.nanoTime();
            Map<String,Object> update = new HashMap<>(action.apply(state));
            long elapsed = System.nanoTime() - started;
            update.put("timingsNanos",Map.of(name,elapsed));
            update.put("trace",List.of(name));
            return update;
        };
    }
}
