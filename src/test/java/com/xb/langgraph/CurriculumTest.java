package com.xb.langgraph;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Contract tests deliberately load lessons by their public names, independently of Spring. */
class CurriculumTest {
    static final String[] NAMES = {"HelloGraphDemo", "SequenceGraphDemo", "ReducerGraphDemo", "ConditionalGraphDemo",
            "LoopGraphDemo", "ParallelFanoutDemo", "SubgraphDemo", "CheckpointDemo", "ThreadIsolationDemo",
            "HumanReviewDemo", "NodeStreamingDemo", "ModelToolLoopDemo", "BoundedRetryDemo", "IdempotencyDemo",
            "NodeTimingDemo", "TicketWorkflowDemo", "ExpenseApprovalDemo", "ResearchWorkflowDemo"};

    static Object demo(int n) throws Exception {
        String name = "com.xb.langgraph.demo%02d.%s".formatted(n, NAMES[n - 1]);
        assertThatCode(() -> Class.forName(name)).as("Lesson %s must exist", name).doesNotThrowAnyException();
        return Class.forName(name).getConstructor().newInstance();
    }
    @SuppressWarnings("unchecked")
    static <T> T call(Object d, String method) throws Exception { return (T) d.getClass().getMethod(method).invoke(d); }
    static RunnableConfig config(Object d, String id) throws Exception {
        return (RunnableConfig) d.getClass().getMethod("config", String.class).invoke(d, id);
    }
    static CompiledGraph<AgentState> compile(Object d) throws Exception {
        StateGraph<AgentState> g = call(d, "graph");
        Set<String> reviews = call(d, "reviewNodes");
        return g.compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).releaseThread(false)
                .interruptBefore(reviews.toArray(String[]::new)).build());
    }
    static AgentState run(int n, Map<String,Object> input) throws Exception {
        Object d = demo(n);
        return compile(d).invoke(GraphInput.args(input), config(d, UUID.randomUUID().toString())).orElseThrow();
    }
    static AgentState sample(int n) throws Exception { return run(n, call(demo(n), "sampleInput")); }
    static Object value(AgentState state, String key) { return state.value(key).orElse(null); }

    @ParameterizedTest @CsvSource({"1,greeting,你好，小明！", "2,published,已发布：LangGraph4j：提纲 → 正文", "4,status,pass",
            "5,count,3", "7,result,父图收到：LANGGRAPH4J", "8,result,checkpoint:done", "9,greeting,你好，甲！",
            "10,status,pending", "11,published,已发布：流式输出：提纲 → 正文", "12,answer,离线工具结果：42",
            "13,attempts,3", "14,balance,110", "16,status,pending", "17,status,pending_manager", "18,status,pending"})
    void sampleOutputs(int n, String key, String expected) throws Exception {
        assertThat(String.valueOf(value(sample(n), key))).isEqualTo(expected);
    }
    @Test void reducersShowSumAppendDedupDuplicatesAndReplacement() throws Exception {
        var s = sample(3);
        assertThat(value(s,"sum")).isEqualTo(5);
        assertThat(value(s,"events")).isEqualTo(List.of("same"));
        assertThat(value(s,"trace")).isEqualTo(List.of("same","same"));
        assertThat(value(s,"replacement")).isEqualTo("second");
    }
    @Test void parallelBranchesMergeBeforeJoin() throws Exception {
        var s = sample(6);
        assertThat(value(s,"facts")).isInstanceOf(List.class);
        assertThat((List<?>)value(s,"facts")).hasSize(2);
        assertThat(value(s,"joined")).isEqualTo("left:LangGraph4j | right:LangGraph4j");
    }
    @Test void timingUsesRealNonnegativeMeasurementsAndTrace() throws Exception {
        var s = sample(15);
        assertThat((List<?>)value(s,"trace")).hasSize(2);
        var durations = (Map<?,?>)value(s,"timingsNanos");
        assertThat(durations).hasSize(2);
        durations.values().forEach(v -> assertThat(((Number)v).longValue()).isGreaterThanOrEqualTo(0));
        assertThat(value(s,"result")).isEqualTo("LANGGRAPH4J");
    }
    @Test void conditionalAndLoopBoundaries() throws Exception {
        assertThat(value(run(4,Map.of("score",59)),"status")).isEqualTo("remedial");
        assertThat(value(run(4,Map.of("score",60)),"status")).isEqualTo("pass");
        assertThat(value(run(5,Map.of("target",10)),"count")).isEqualTo(10);
        assertThatThrownBy(() -> run(4,Map.of("score",101))).hasStackTraceContaining("score");
        assertThatThrownBy(() -> run(4,Map.of("score","60"))).hasStackTraceContaining("score");
        assertThatThrownBy(() -> run(5,Map.of("target",0))).hasStackTraceContaining("target");
    }
    @Test void checkpointsExposeHistoryAndHistoricalConfiguration() throws Exception {
        var d = demo(8); var g = compile(d); var c = config(d,"history");
        g.invoke(GraphInput.args(call(d,"sampleInput")),c);
        var history = g.getStateHistory(c);
        assertThat(history).hasSizeGreaterThanOrEqualTo(3);
        var historical = history.stream().filter(s -> "checkpoint:one".equals(value(s.state(),"result"))).findFirst().orElseThrow();
        assertThat(value(g.getState(historical.config()).state(),"result")).isEqualTo("checkpoint:one");
        assertThat(value(g.getState(c).state(),"result")).isEqualTo("checkpoint:done");
    }
    @Test void threadsUseOneCompiledGraphAndOneSaverWithoutLeaking() throws Exception {
        var d = demo(9); var g = compile(d);
        g.invoke(GraphInput.args(Map.of("name","甲")),config(d,"a"));
        g.invoke(GraphInput.args(Map.of("name","乙")),config(d,"b"));
        assertThat(value(g.getState(config(d,"a")).state(),"greeting")).isEqualTo("你好，甲！");
        assertThat(value(g.getState(config(d,"b")).state(),"greeting")).isEqualTo("你好，乙！");
    }
    static AgentState reviewed(int n, boolean approved) throws Exception {
        var d = demo(n); var g = compile(d); String id = UUID.randomUUID().toString();
        var c = config(d,id); g.invoke(GraphInput.args(call(d,"sampleInput")),c);
        assertThat(g.getState(c).next()).isEqualTo("review");
        g.updateState(c, Map.of("approved",approved));
        return g.invoke(GraphInput.resume(),config(d,id)).orElseThrow();
    }
    @Test void humanReviewExecutesDecisionNodeOnResume() throws Exception {
        assertThat(value(reviewed(10,true),"status")).isEqualTo("approved");
        assertThat(value(reviewed(10,false),"status")).isEqualTo("rejected");
    }
    @Test void streamingIsNativeOrderedNodeOutput() throws Exception {
        var d = demo(11); var nodes = compile(d).stream(GraphInput.args(call(d,"sampleInput")),config(d,"stream"))
                .stream().map(NodeOutput::node).toList();
        assertThat(nodes).containsSubsequence("outline","draft","publish");
    }
    @Test void retryIsBoundedAndAttemptsArePerExecution() throws Exception {
        var d = demo(13); var g = compile(d);
        for (String id : List.of("first","second")) {
            var s = g.invoke(GraphInput.args(Map.of("failuresBeforeSuccess",2)),config(d,id)).orElseThrow();
            assertThat(value(s,"attempts")).isEqualTo(3);
            assertThat(value(s,"status")).isEqualTo("succeeded");
        }
        var failed = run(13,Map.of("failuresBeforeSuccess",3));
        assertThat(value(failed,"status")).isEqualTo("exhausted");
        assertThat(value(failed,"attempts")).isEqualTo(3);
    }
    @Test void idempotencyKeepsBothAttemptsButOnlyOneBalanceChange() throws Exception {
        var s = sample(14);
        assertThat(value(s,"trace")).isEqualTo(List.of("apply:order-1","apply:order-1"));
        assertThat(value(s,"appliedOperations")).isEqualTo(List.of("order-1"));
        assertThat(value(s,"balance")).isEqualTo(110);
    }
    @Test void idempotencyPreservesRetainedBalanceAndCreditsEachNewOperationOnce() throws Exception {
        var d = demo(14); var g = compile(d); var c = config(d,"retained-balance");
        Map<String,Object> first = call(d,"sampleInput");
        assertThat(value(g.invoke(GraphInput.args(first),c).orElseThrow(),"balance")).isEqualTo(110);
        var repeated = g.invoke(GraphInput.args(first),c).orElseThrow();
        assertThat(value(repeated,"balance")).isEqualTo(110);
        assertThat(value(repeated,"appliedOperations")).isEqualTo(List.of("order-1"));
        var next = Map.<String,Object>of("operationId","order-2","initialBalance",100,"amount",7);
        assertThat(value(g.invoke(GraphInput.args(next),c).orElseThrow(),"balance")).isEqualTo(117);
        var nextRepeated = g.invoke(GraphInput.args(next),c).orElseThrow();
        assertThat(value(nextRepeated,"balance")).isEqualTo(117);
        assertThat(value(nextRepeated,"appliedOperations")).isEqualTo(List.of("order-1","order-2"));
    }
    @ParameterizedTest @CsvSource({"initialBalance,-1", "amount,0", "operationId, "})
    void retainedIdempotencyStillValidatesEveryInput(String key, String invalid) throws Exception {
        var d = demo(14); var g = compile(d); var c = config(d,"invalid-retained-" + key);
        Map<String,Object> input = new HashMap<>(call(d,"sampleInput"));
        g.invoke(GraphInput.args(input),c).orElseThrow();
        input.put(key,key.equals("operationId") ? " " : Integer.valueOf(invalid));
        assertThatThrownBy(() -> g.invoke(GraphInput.args(input),c)).hasStackTraceContaining(key);
        assertThat(value(g.getState(c).state(),"balance")).isEqualTo(110);
    }
    @ParameterizedTest @CsvSource({"2", "11"})
    void maximumLengthTopicCanGrowThroughOutlineAndDraft(int n) throws Exception {
        String topic = "题".repeat(2000);
        assertThat(value(run(n,Map.of("topic",topic)),"published"))
                .isEqualTo("已发布：" + topic + "：提纲 → 正文");
    }
    @ParameterizedTest @CsvSource({"7,父图收到：", "15,''"})
    void uppercaseExpansionIsNotLimitedAsExternalInput(int n, String prefix) throws Exception {
        assertThat(value(run(n,Map.of("topic","ß".repeat(2000))),"result"))
                .isEqualTo(prefix + "SS".repeat(2000));
    }
    @Test void approvedResearchPublishesLongGeneratedSynthesis() throws Exception {
        var d = demo(18); var g = compile(d); var c = config(d,"long-research");
        var pending = g.invoke(GraphInput.args(Map.of("topic","题".repeat(2000))),c).orElseThrow();
        String synthesis = (String)value(pending,"synthesis");
        assertThat(synthesis).hasSizeGreaterThan(2000).contains("market-001","risk-001");
        assertThat(g.getState(c).next()).isEqualTo("review");
        g.updateState(c,Map.of("approved",true));
        var published = g.invoke(GraphInput.resume(),c).orElseThrow();
        assertThat(value(published,"status")).isEqualTo("published");
        assertThat(value(published,"report")).isEqualTo(synthesis);
    }
    @ParameterizedTest @CsvSource({"1,name", "2,topic", "6,topic", "7,topic", "8,label", "9,name",
            "10,content", "11,topic", "14,operationId", "15,topic", "16,ticket", "18,topic"})
    void externalTextStillRejectsOversizedBlankAndNonStringValues(int n, String key) throws Exception {
        for (Object invalid : List.of("x".repeat(2001)," \t ",42)) {
            Map<String,Object> input = new HashMap<>(call(demo(n),"sampleInput"));
            input.put(key,invalid);
            assertThatThrownBy(() -> run(n,input)).hasStackTraceContaining(key);
        }
    }
    @Test void externalTextIsStrippedBeforeUse() throws Exception {
        assertThat(value(run(2,Map.of("topic","  topic \t")),"published"))
                .isEqualTo("已发布：topic：提纲 → 正文");
    }
    @Test void internalTextRequiresStringButPreservesGeneratedContent() {
        String generated = "  " + "x".repeat(2001) + "\n";
        var state = new LearningState(Map.of("generated",generated,"empty","","wrong",42));
        assertThat(state.text("generated")).isEqualTo(generated);
        assertThat(state.text("empty")).isEmpty();
        assertThatThrownBy(() -> state.text("wrong")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state.text("missing")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void ticketUsesCategorySpecificKnowledgeAndPublishesOnlyAfterApproval() throws Exception {
        var refund = sample(16);
        assertThat(value(refund,"category")).isEqualTo("refund");
        assertThat(value(refund,"knowledge")).isEqualTo("退款政策：7天内可申请退款（离线规则）。");
        var inquiry = run(16,Map.of("ticket","如何查看物流？"));
        assertThat(value(inquiry,"category")).isEqualTo("inquiry");
        assertThat(value(inquiry,"knowledge")).isEqualTo("咨询政策：在订单页面查看物流（离线规则）。");
        assertThat(value(reviewed(16,true),"status")).isEqualTo("published");
        assertThat(value(reviewed(16,false),"status")).isEqualTo("rejected");
    }
    @ParameterizedTest @CsvSource({"500,true,true,paid", "1500,true,true,paid", "1500,false,true,rejected", "1500,true,false,rejected"})
    void expenseHasSeparateManagerAndFinanceInterruptions(int amount, boolean manager, boolean finance, String expected) throws Exception {
        var d = demo(17); var g = compile(d); String id = UUID.randomUUID().toString();
        g.invoke(GraphInput.args(Map.of("amount",amount)),config(d,id));
        assertThat(g.getState(config(d,id)).next()).isEqualTo("managerReview");
        g.updateState(config(d,id),Map.of("managerApproved",manager));
        var s = g.invoke(GraphInput.resume(),config(d,id)).orElseThrow();
        if (manager && amount > 1000) {
            assertThat(value(s,"status")).isEqualTo("pending_finance");
            assertThat(g.getState(config(d,id)).next()).isEqualTo("financeReview");
            g.updateState(config(d,id),Map.of("financeApproved",finance));
            s = g.invoke(GraphInput.resume(),config(d,id)).orElseThrow();
        }
        assertThat(value(s,"status")).isEqualTo(expected);
        if (!expected.equals("paid")) assertThat(value(s,"paid")).isNotEqualTo(true);
    }
    @Test void researchUsesExplicitFixturesAndCitationsAfterReview() throws Exception {
        var pending = sample(18);
        assertThat(value(pending,"dataMode")).isEqualTo("offline-fixture");
        assertThat((String)value(pending,"synthesis")).contains("market-001","risk-001");
        var approved = reviewed(18,true);
        assertThat(value(approved,"status")).isEqualTo("published");
        assertThat(value(approved,"citations")).isEqualTo(List.of("market-001","risk-001"));
        assertThat(value(reviewed(18,false),"status")).isEqualTo("rejected");
    }
    @Test void allLessonsExposeScalarSamplesChineseMetadataAndNativeDiagrams() throws Exception {
        for (int n=1;n<=18;n++) {
            Object d=demo(n);
            assertThat((String)call(d,"id")).isEqualTo("demo%02d".formatted(n));
            assertThat((String)call(d,"title")).isNotBlank();
            assertThat((String)call(d,"level")).isIn("基础","高阶","实战");
            Map<String,Object> input=call(d,"sampleInput");
            input.values().forEach(v -> assertThat(v instanceof String || v instanceof Integer || v instanceof Boolean).isTrue());
            StateGraph<AgentState> graph=call(d,"graph");
            assertThat(graph.getGraph(GraphRepresentation.Type.MERMAID,"lesson",true).content()).contains("flowchart TD");
        }
    }
}
