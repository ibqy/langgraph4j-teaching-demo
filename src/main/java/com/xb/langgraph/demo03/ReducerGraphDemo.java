package com.xb.langgraph.demo03;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class ReducerGraphDemo implements GraphDemo {
    public String id() { return "demo03"; }
    public String title() { return "状态合并：累加、追加与覆盖"; }
    public String level() { return "基础"; }
    public Map<String,Object> sampleInput() { return Map.of("first",2,"second",3); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("first",node_async(s -> Map.of("sum",s.integer("first",0,100),"events",List.of("same"),
                        "trace",List.of("same"),"replacement","first")))
                .addNode("second",node_async(s -> Map.of("sum",s.integer("second",0,100),"events",List.of("same"),
                        "trace",List.of("same"),"replacement","second")))
                .addEdge(StateGraph.START,"first").addEdge("first","second").addEdge("second",StateGraph.END);
    }
}
