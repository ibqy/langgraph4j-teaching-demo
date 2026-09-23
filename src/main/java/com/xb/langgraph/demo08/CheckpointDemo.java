package com.xb.langgraph.demo08;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.Map;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/** 编译时使用 MemorySaver 和 releaseThread(false)，以历史 config 查询旧快照。 */
@Component
public class CheckpointDemo implements GraphDemo {
    public String id() { return "demo08"; }
    public String title() { return "检查点：状态历史与时间旅行"; }
    public String level() { return "高阶"; }
    public Map<String,Object> sampleInput() { return Map.of("label","checkpoint"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("first",node_async(s -> Map.of("result",s.inputText("label") + ":one")))
                .addNode("second",node_async(s -> Map.of("result",s.inputText("label") + ":done")))
                .addEdge(StateGraph.START,"first").addEdge("first","second").addEdge("second",StateGraph.END);
    }
}
