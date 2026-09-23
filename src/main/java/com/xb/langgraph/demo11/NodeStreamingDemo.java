package com.xb.langgraph.demo11;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.Map;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/** stream(...).stream() 顺序消费原生 NodeOutput；不是 token 流，也不是 Reactor Flux。 */
@Component
public class NodeStreamingDemo implements GraphDemo {
    public String id() { return "demo11"; }
    public String title() { return "节点流：逐步观察状态"; }
    public String level() { return "高阶"; }
    public Map<String,Object> sampleInput() { return Map.of("topic","流式输出"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("outline",node_async(s -> Map.of("outline",s.inputText("topic") + "：提纲")))
                .addNode("draft",node_async(s -> Map.of("draft",s.text("outline") + " → 正文")))
                .addNode("publish",node_async(s -> Map.of("published","已发布：" + s.text("draft"))))
                .addEdge(StateGraph.START,"outline").addEdge("outline","draft")
                .addEdge("draft","publish").addEdge("publish",StateGraph.END);
    }
}
