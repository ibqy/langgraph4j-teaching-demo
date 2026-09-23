package com.xb.langgraph.demo06;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class ParallelFanoutDemo implements GraphDemo {
    private final Function<String,String> leftSource;
    private final Function<String,String> rightSource;
    public ParallelFanoutDemo() { this(topic -> "left:" + topic,topic -> "right:" + topic); }
    /** 替换分支的数据来源，不改变原生并行调度及状态合并。 */
    public ParallelFanoutDemo(Function<String,String> leftSource, Function<String,String> rightSource) {
        this.leftSource = Objects.requireNonNull(leftSource);
        this.rightSource = Objects.requireNonNull(rightSource);
    }
    public String id() { return "demo06"; }
    public String title() { return "原生并行：分叉、合并、汇总"; }
    public String level() { return "基础"; }
    public Map<String,Object> sampleInput() { return Map.of("topic","LangGraph4j"); }
    public RunnableConfig config(String threadId) {
        return RunnableConfig.builder().threadId(threadId).addParallelNodeExecutor("dispatch",ForkJoinPool.commonPool()).build();
    }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("dispatch",node_async(s -> { s.inputText("topic"); return Map.of(); }))
                .addNode("left",node_async(s -> Map.of("facts",List.of(leftSource.apply(s.inputText("topic"))))))
                .addNode("right",node_async(s -> Map.of("facts",List.of(rightSource.apply(s.inputText("topic"))))))
                .addNode("join",node_async(s -> Map.of("joined",String.join(" | ",s.<String>list("facts").stream().sorted().toList()))))
                .addEdge(StateGraph.START,"dispatch").addEdge("dispatch","left").addEdge("dispatch","right")
                .addEdge("left","join").addEdge("right","join").addEdge("join",StateGraph.END);
    }
}
