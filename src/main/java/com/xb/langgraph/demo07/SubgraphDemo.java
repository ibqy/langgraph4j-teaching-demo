package com.xb.langgraph.demo07;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class SubgraphDemo implements GraphDemo {
    public String id() { return "demo07"; }
    public String title() { return "子图：编译与复用"; }
    public String level() { return "高阶"; }
    public Map<String,Object> sampleInput() { return Map.of("topic","LangGraph4j"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        var child = new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("normalize",node_async(s -> Map.of("normalized",s.inputText("topic").toUpperCase(Locale.ROOT))))
                .addNode("childResult",node_async(s -> Map.of("childOutput",s.text("normalized"))))
                .addEdge(StateGraph.START,"normalize").addEdge("normalize","childResult").addEdge("childResult",StateGraph.END)
                .compile();
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("child",child)
                .addNode("parent",node_async(s -> Map.of("result","父图收到：" + s.text("childOutput"))))
                .addEdge(StateGraph.START,"child").addEdge("child","parent").addEdge("parent",StateGraph.END);
    }
}
