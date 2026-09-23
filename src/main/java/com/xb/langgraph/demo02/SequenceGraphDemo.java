package com.xb.langgraph.demo02;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.Map;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class SequenceGraphDemo implements GraphDemo {
    public String id() { return "demo02"; }
    public String title() { return "顺序执行：提纲到发布"; }
    public String level() { return "基础"; }
    public Map<String,Object> sampleInput() { return Map.of("topic","LangGraph4j"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("outline",node_async(s -> Map.of("outline",s.inputText("topic") + "：提纲")))
                .addNode("draft",node_async(s -> Map.of("draft",s.text("outline") + " → 正文")))
                .addNode("publish",node_async(s -> Map.of("published","已发布：" + s.text("draft"))))
                .addEdge(StateGraph.START,"outline").addEdge("outline","draft")
                .addEdge("draft","publish").addEdge("publish",StateGraph.END);
    }
}
