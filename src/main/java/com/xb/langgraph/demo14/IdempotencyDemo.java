package com.xb.langgraph.demo14;

import com.xb.langgraph.*;
import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/** 仅演示状态级幂等；不是跨进程、数据库事务或外部支付的 exactly-once 保证。 */
@Component
public class IdempotencyDemo implements GraphDemo {
    public String id() { return "demo14"; }
    public String title() { return "状态级幂等：重复操作不重复入账"; }
    public String level() { return "高阶"; }
    public Map<String,Object> sampleInput() { return Map.of("operationId","order-1","initialBalance",100,"amount",10); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        return new StateGraph<>(LearningState.SCHEMA,LearningState::new)
                .addNode("initialize",node_async(s -> {
                    s.inputText("operationId"); s.integer("amount",1,100000);
                    int initialBalance = s.integer("initialBalance",0,1000000);
                    return s.value("balance").isPresent() ? Map.of() : Map.of("balance",initialBalance);
                }))
                .addNode("applyFirst",node_async(this::apply))
                .addNode("applyAgain",node_async(this::apply))
                .addEdge(StateGraph.START,"initialize").addEdge("initialize","applyFirst")
                .addEdge("applyFirst","applyAgain").addEdge("applyAgain",StateGraph.END);
    }
    private Map<String,Object> apply(LearningState s) {
        String operation = s.inputText("operationId");
        var update = new HashMap<String,Object>();
        update.put("trace",List.of("apply:" + operation));
        if (!s.<String>list("appliedOperations").contains(operation)) {
            update.put("balance",Math.addExact(s.count("balance"),s.integer("amount",1,100000)));
            update.put("appliedOperations",List.of(operation));
        }
        return update;
    }
}
