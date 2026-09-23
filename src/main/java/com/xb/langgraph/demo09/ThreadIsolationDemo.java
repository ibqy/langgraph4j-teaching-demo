package com.xb.langgraph.demo09;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.Map;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/** threadId 是检查点命名空间，不是 Java 线程号，也不是访问控制。 */
@Component
public class ThreadIsolationDemo implements GraphDemo {
    public String id() { return "demo09"; }
    public String title() { return "会话隔离：共享图、独立状态"; }
    public String level() { return "高阶"; }
    public Map<String,Object> sampleInput() { return Map.of("name","甲"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("greet",node_async(s -> Map.of("greeting","你好，" + s.inputText("name") + "！")))
                .addEdge(StateGraph.START,"greet").addEdge("greet",StateGraph.END);
    }
}
