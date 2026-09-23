package com.xb.langgraph.demo01;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.Map;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class HelloGraphDemo implements GraphDemo {
    public String id() { return "demo01"; }
    public String title() { return "第一个图：问候"; }
    public String level() { return "基础"; }
    public Map<String,Object> sampleInput() { return Map.of("name","小明"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA, LearningState::new)
                .addNode("hello",node_async(s -> Map.of("greeting","你好，" + s.inputText("name") + "！")))
                .addEdge(StateGraph.START,"hello").addEdge("hello",StateGraph.END);
    }
}
