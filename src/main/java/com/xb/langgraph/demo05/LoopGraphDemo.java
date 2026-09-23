package com.xb.langgraph.demo05;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.Map;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Component
public class LoopGraphDemo implements GraphDemo {
    public String id() { return "demo05"; }
    public String title() { return "循环：有界计数"; }
    public String level() { return "基础"; }
    public Map<String,Object> sampleInput() { return Map.of("target",3); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("initialize",node_async(s -> { s.integer("target",1,10); return Map.of("count",0); }))
                .addNode("increment",node_async(s -> Map.of("count",s.count("count") + 1)))
                .addEdge(StateGraph.START,"initialize").addEdge("initialize","increment")
                .addConditionalEdges("increment",edge_async(s -> s.count("count") < s.count("target") ? "again" : "done"),
                        Map.of("again","increment","done",StateGraph.END));
    }
}
