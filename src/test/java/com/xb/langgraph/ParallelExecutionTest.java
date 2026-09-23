package com.xb.langgraph;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;

class ParallelExecutionTest {
    @ParameterizedTest @ValueSource(ints={6,18})
    void bothBranchActionsEnterBeforeEitherCanFinish(int number) throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        Function<String,String> left = topic -> awaitBoth(entered,"left:" + topic);
        Function<String,String> right = topic -> awaitBoth(entered,"right:" + topic);
        var type = CurriculumTest.demo(number).getClass();
        assertThatCode(() -> type.getConstructor(Function.class,Function.class))
                .as("Branch fact providers must be replaceable without replacing the graph engine").doesNotThrowAnyException();
        Object demo = type.getConstructor(Function.class,Function.class).newInstance(left,right);
        StateGraph<AgentState> graph = CurriculumTest.call(demo,"graph");
        Set<String> reviews = CurriculumTest.call(demo,"reviewNodes");
        var compiled = graph.compile(CompileConfig.builder()
                .checkpointSaver(new org.bsc.langgraph4j.checkpoint.MemorySaver()).releaseThread(false)
                .interruptBefore(reviews.toArray(String[]::new)).build());
        var state = compiled.invoke(GraphInput.args(CurriculumTest.call(demo,"sampleInput")),CurriculumTest.config(demo,"parallel")).orElseThrow();
        assertThat(entered.getCount()).isZero();
        assertThat((java.util.List<?>)CurriculumTest.value(state,"facts")).hasSize(2);
    }
    private static String awaitBoth(CountDownLatch entered, String result) {
        entered.countDown();
        try {
            // Timeout is a deadlock guard, not a performance assertion: neither action may finish alone.
            if (!entered.await(10,TimeUnit.SECONDS)) throw new AssertionError("Branches did not overlap");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        return result;
    }
}
