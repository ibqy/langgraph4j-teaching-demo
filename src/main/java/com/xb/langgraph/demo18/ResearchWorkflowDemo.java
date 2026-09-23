package com.xb.langgraph.demo18;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/** 固定教学资料，不表示实时检索、市场预测或真实投资建议。 */
@Component
public class ResearchWorkflowDemo implements GraphDemo {
    private final Function<String,String> marketSource;
    private final Function<String,String> riskSource;
    public ResearchWorkflowDemo() {
        this(topic -> "[market-001] 教学资料：企业关注效率提升。",topic -> "[risk-001] 教学资料：需评估隐私与幻觉风险。");
    }
    /** 可替换教学资料内容；来源标识约定分别为 market-001 与 risk-001。 */
    public ResearchWorkflowDemo(Function<String,String> marketSource, Function<String,String> riskSource) {
        this.marketSource = Objects.requireNonNull(marketSource);
        this.riskSource = Objects.requireNonNull(riskSource);
    }
    public String id() { return "demo18"; }
    public String title() { return "研究工作流：并行资料、引用与人工发布"; }
    public String level() { return "实战"; }
    public Map<String,Object> sampleInput() { return Map.of("topic","企业AI助手"); }
    public Set<String> reviewNodes() { return Set.of("review"); }
    public RunnableConfig config(String threadId) {
        return RunnableConfig.builder().threadId(threadId).addParallelNodeExecutor("dispatch",ForkJoinPool.commonPool()).build();
    }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("dispatch",node_async(s -> { s.inputText("topic"); return Map.of("dataMode","offline-fixture"); }))
                .addNode("market",node_async(s -> Map.of("facts",List.of(marketSource.apply(s.inputText("topic"))))))
                .addNode("risk",node_async(s -> Map.of("facts",List.of(riskSource.apply(s.inputText("topic"))))))
                .addNode("synthesis",node_async(s -> Map.of("synthesis",s.inputText("topic") + "（离线固定资料）\n" +
                        String.join("\n",s.<String>list("facts").stream().sorted().toList()),"status","pending")))
                .addNode("review",node_async(s -> Map.of("status",s.decision("approved") ? "approved" : "rejected")))
                .addNode("publish",node_async(s -> Map.of("report",s.text("synthesis"),"citations",List.of("market-001","risk-001"),"status","published")))
                .addNode("reject",node_async(s -> Map.of("status","rejected")))
                .addEdge(StateGraph.START,"dispatch").addEdge("dispatch","market").addEdge("dispatch","risk")
                .addEdge("market","synthesis").addEdge("risk","synthesis").addEdge("synthesis","review")
                .addConditionalEdges("review",edge_async(s -> s.text("status")),Map.of("approved","publish","rejected","reject"))
                .addEdge("publish",StateGraph.END).addEdge("reject",StateGraph.END);
    }
}
