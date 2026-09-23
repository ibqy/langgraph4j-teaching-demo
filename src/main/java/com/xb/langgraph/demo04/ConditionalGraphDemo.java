package com.xb.langgraph.demo04;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.Map;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Component
public class ConditionalGraphDemo implements GraphDemo {
    public String id() { return "demo04"; }
    public String title() { return "条件路由：成绩分流"; }
    public String level() { return "基础"; }
    public Map<String,Object> sampleInput() { return Map.of("score",85); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("evaluate",node_async(s -> Map.of("passed",s.integer("score",0,100) >= 60)))
                .addNode("pass",node_async(s -> Map.of("status","pass")))
                .addNode("remedial",node_async(s -> Map.of("status","remedial")))
                .addEdge(StateGraph.START,"evaluate")
                .addConditionalEdges("evaluate",edge_async(s -> s.decision("passed") ? "pass" : "remedial"),
                        Map.of("pass","pass","remedial","remedial"))
                .addEdge("pass",StateGraph.END).addEdge("remedial",StateGraph.END);
    }
}
