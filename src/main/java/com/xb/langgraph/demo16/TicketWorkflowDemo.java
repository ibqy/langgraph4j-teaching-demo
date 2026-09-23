package com.xb.langgraph.demo16;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Component
public class TicketWorkflowDemo implements GraphDemo {
    public String id() { return "demo16"; }
    public String title() { return "客服工单：分类、知识与人工审核"; }
    public String level() { return "实战"; }
    public Map<String,Object> sampleInput() { return Map.of("ticket","收到商品后想申请退款"); }
    public Set<String> reviewNodes() { return Set.of("review"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("classify",node_async(s -> Map.of("category",s.inputText("ticket").contains("退款") ? "refund" : "inquiry","dataMode","offline-rules")))
                .addNode("refundKnowledge",node_async(s -> Map.of("knowledge","退款政策：7天内可申请退款（离线规则）。")))
                .addNode("inquiryKnowledge",node_async(s -> Map.of("knowledge","咨询政策：在订单页面查看物流（离线规则）。")))
                .addNode("draft",node_async(s -> Map.of("draft","您好，" + s.text("knowledge"),"status","pending")))
                .addNode("review",node_async(s -> Map.of("status",s.decision("approved") ? "approved" : "rejected")))
                .addNode("publish",node_async(s -> Map.of("reply",s.text("draft"),"status","published")))
                .addNode("reject",node_async(s -> Map.of("status","rejected")))
                .addEdge(StateGraph.START,"classify")
                .addConditionalEdges("classify",edge_async(s -> s.text("category")),Map.of("refund","refundKnowledge","inquiry","inquiryKnowledge"))
                .addEdge("refundKnowledge","draft").addEdge("inquiryKnowledge","draft").addEdge("draft","review")
                .addConditionalEdges("review",edge_async(s -> s.text("status")),Map.of("approved","publish","rejected","reject"))
                .addEdge("publish",StateGraph.END).addEdge("reject",StateGraph.END);
    }
}
