package com.xb.langgraph;

import org.bsc.langgraph4j.*;
import java.util.Map;
import java.util.Set;

public interface GraphDemo {
    String id();
    String title();
    String level();
    Map<String,Object> sampleInput();
    StateGraph<LearningState> graph() throws GraphStateException;
    default Set<String> reviewNodes() { return Set.of(); }
    default RunnableConfig config(String threadId) { return RunnableConfig.builder().threadId(threadId).build(); }
}
