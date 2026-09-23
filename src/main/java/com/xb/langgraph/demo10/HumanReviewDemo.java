package com.xb.langgraph.demo10;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class HumanReviewDemo implements GraphDemo {
    public String id() { return "demo10"; }
    public String title() { return "人工审核：中断与恢复"; }
    public String level() { return "高阶"; }
    public Map<String,Object> sampleInput() { return Map.of("content","待审核文章"); }
    public Set<String> reviewNodes() { return Set.of("review"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("prepare",node_async(s -> Map.of("draft",s.inputText("content"),"status","pending")))
                .addNode("review",node_async(s -> Map.of("status",s.decision("approved") ? "approved" : "rejected")))
                .addEdge(StateGraph.START,"prepare").addEdge("prepare","review").addEdge("review",StateGraph.END);
    }
}
