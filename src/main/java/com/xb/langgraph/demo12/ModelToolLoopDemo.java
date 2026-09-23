package com.xb.langgraph.demo12;

import com.xb.langgraph.*;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.*;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Component
public class ModelToolLoopDemo implements GraphDemo {
    private final ChatModel model;
    private final LC4jToolService tools = LC4jToolService.builder().toolsFromObject(new ArithmeticTools()).build();
    public ModelToolLoopDemo() { this(new OfflineArithmeticChatModel()); }
    @Autowired public ModelToolLoopDemo(ChatModel model) { this.model = Objects.requireNonNull(model); }
    public String id() { return "demo12"; }
    public String title() { return "模型与工具：原生工具调用闭环"; }
    public String level() { return "高阶"; }
    public Map<String,Object> sampleInput() { return Map.of("a",19,"b",23); }
    public StateGraph<LearningState> graph() throws GraphStateException {
        // ChatMessage 不是普通 Serializable；使用官方 LC4jStateSerializer 注册消息序列化器。
        return new StateGraph<>(LearningState.SCHEMA,new LC4jStateSerializer<>(LearningState::new))
                .addNode("prepare",node_async(s -> Map.of("messages",List.of(UserMessage.from("请调用 add 计算 " +
                        s.integer("a",-1000,1000) + " + " + s.integer("b",-1000,1000))),"modelTurns",0,
                        "modelMode",model instanceof OfflineArithmeticChatModel ? "offline-deterministic" : "openai-compatible")))
                .addNode("model",node_async(s -> {
                    int turns = s.count("modelTurns") + 1;
                    if (turns > 6) throw new IllegalStateException("模型工具循环超过6轮上限");
                    var answer = model.chat(ChatRequest.builder().messages(s.<ChatMessage>list("messages"))
                            .toolSpecifications(tools.toolSpecifications()).build()).aiMessage();
                    var update = new HashMap<String,Object>();
                    update.put("messages",List.of(answer)); update.put("modelTurns",turns);
                    if (!answer.hasToolExecutionRequests()) update.put("answer",Objects.requireNonNullElse(answer.text(),""));
                    return update;
                }))
                .addNode("tools",(LearningState s) -> {
                    var last = (AiMessage)s.<ChatMessage>list("messages").getLast();
                    for (var request : last.toolExecutionRequests()) {
                        if (tools.toolSpecifications().stream().noneMatch(spec -> spec.name().equals(request.name())))
                            throw new IllegalArgumentException("Unknown tool: " + request.name());
                    }
                    return tools.execute(last.toolExecutionRequests(),InvocationContext.builder().build(),"messages")
                            .thenApply(command -> command.update());
                })
                .addEdge(StateGraph.START,"prepare").addEdge("prepare","model")
                .addConditionalEdges("model",edge_async(s -> ((AiMessage)s.<ChatMessage>list("messages").getLast()).hasToolExecutionRequests() ? "tools" : "done"),
                        Map.of("tools","tools","done",StateGraph.END))
                .addEdge("tools","model");
    }
    public static class ArithmeticTools {
        @Tool("将两个范围为-1000到1000的整数相加")
        public int add(int a, int b) {
            if (a < -1000 || a > 1000 || b < -1000 || b > 1000) throw new IllegalArgumentException("工具参数超出范围");
            return Math.addExact(a,b);
        }
    }
}
