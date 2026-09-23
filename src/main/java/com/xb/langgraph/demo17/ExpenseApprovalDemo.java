package com.xb.langgraph.demo17;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Component
public class ExpenseApprovalDemo implements GraphDemo {
    public String id() { return "demo17"; }
    public String title() { return "报销审批：经理与财务多级中断"; }
    public String level() { return "实战"; }
    public Map<String,Object> sampleInput() { return Map.of("amount",1500); }
    public Set<String> reviewNodes() { return Set.of("managerReview","financeReview"); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("validate",node_async(s -> { s.integer("amount",1,1000000); return Map.of("status","pending_manager"); }))
                .addNode("managerReview",node_async(s -> Map.of("status",!s.decision("managerApproved") ? "rejected" :
                        s.count("amount") > 1000 ? "pending_finance" : "approved")))
                .addNode("financeReview",node_async(s -> Map.of("status",s.decision("financeApproved") ? "approved" : "rejected")))
                .addNode("pay",node_async(s -> Map.of("status","paid","paid",true,"paymentMode","offline-simulation")))
                .addNode("reject",node_async(s -> Map.of("status","rejected","paid",false)))
                .addEdge(StateGraph.START,"validate").addEdge("validate","managerReview")
                .addConditionalEdges("managerReview",edge_async(s -> s.text("status")),Map.of("rejected","reject","pending_finance","financeReview","approved","pay"))
                .addConditionalEdges("financeReview",edge_async(s -> s.text("status")),Map.of("approved","pay","rejected","reject"))
                .addEdge("pay",StateGraph.END).addEdge("reject",StateGraph.END);
    }
}
