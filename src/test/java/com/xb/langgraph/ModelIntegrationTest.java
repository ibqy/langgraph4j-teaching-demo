package com.xb.langgraph;

import com.xb.langgraph.demo12.ModelConfiguration;
import com.xb.langgraph.demo12.ModelToolLoopDemo;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.GraphInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ModelIntegrationTest {
    @Test void realChatModelRequestsRealToolThenReadsItsResult() throws Exception {
        var result = CurriculumTest.run(12,Map.of("a",9,"b",8));
        assertThat(CurriculumTest.value(result,"answer")).isEqualTo("离线工具结果：17");
        assertThat(CurriculumTest.value(result,"modelMode")).isEqualTo("offline-deterministic");
        List<?> messages = (List<?>)CurriculumTest.value(result,"messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((AiMessage)messages.get(1)).toolExecutionRequests()).singleElement().satisfies(r -> assertThat(r.name()).isEqualTo("add"));
        assertThat(((ToolExecutionResultMessage)messages.get(2)).text()).isEqualTo("17");
        assertThat(((AiMessage)messages.get(3)).text()).isEqualTo("离线工具结果：17");
    }
    @Test void unknownToolFailsBeforeAnotherModelCall() throws Exception {
        var calls = new AtomicInteger();
        var demo = new ModelToolLoopDemo(requestingTool("unknown_tool","{\"a\":1,\"b\":2}",calls));
        var graph = CurriculumTest.compile(demo);
        assertThatThrownBy(() -> graph.invoke(GraphInput.args(demo.sampleInput()),demo.config("unknown-tool")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class).hasStackTraceContaining("Unknown tool: unknown_tool");
        assertThat(calls).hasValue(1);
    }
    @ParameterizedTest @ValueSource(strings = {"{\"a\":1001,\"b\":2}", "not-json"})
    void invalidToolArgumentsTerminateWithinTheModelTurnBound(String arguments) throws Exception {
        var calls = new AtomicInteger();
        var demo = new ModelToolLoopDemo(requestingTool("add",arguments,calls));
        var graph = CurriculumTest.compile(demo);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThatThrownBy(() -> graph.invoke(GraphInput.args(demo.sampleInput()),demo.config("invalid-arguments")))
                    .isInstanceOf(Exception.class);
            assertThat(calls.get()).isBetween(1,6);
        });
    }
    private static ChatModel requestingTool(String name, String arguments, AtomicInteger calls) {
        return new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                calls.incrementAndGet();
                assertThat(request.toolSpecifications()).extracting(spec -> spec.name()).containsExactly("add");
                return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("test-call-" + calls.get()).name(name).arguments(arguments).build())).build();
            }
        };
    }
    @ParameterizedTest @ValueSource(strings = {"http://example.invalid/v1", "http://localhost.example.invalid/v1",
            "http://127.0.0.2/v1", "http://localhost./v1", "http://[::2]/v1"})
    void remotePlainHttpIsRejectedBeforeModelConstruction(String url) {
        assertThatThrownBy(() -> new ModelConfiguration().chatModel("openai","test-key",url,"test-model"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
    }
    @ParameterizedTest @ValueSource(strings = {"https://example.invalid/v1", "http://localhost:8080/v1",
            "http://127.0.0.1:8080/v1", "http://[::1]:8080/v1"})
    void httpsAndExactLoopbackHttpAllowConstructionWithoutNetwork(String url) {
        assertThat(new ModelConfiguration().chatModel("openai","test-key",url,"test-model"))
                .isInstanceOf(ChatModel.class);
    }
    @ParameterizedTest @ValueSource(strings = {"https://user:pass@example.invalid/v1",
            "http://user:pass@localhost/v1"})
    void modelEndpointNeverAllowsUserInfo(String url) {
        assertThatThrownBy(() -> new ModelConfiguration().chatModel("openai","test-key",url,"test-model"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void providerRequiresExplicitSelectionAndCompleteConfiguration() throws Exception {
        String name="com.xb.langgraph.demo12.ModelConfiguration";
        assertThatCode(() -> Class.forName(name)).doesNotThrowAnyException();
        Object config=Class.forName(name).getConstructor().newInstance();
        var method=config.getClass().getMethod("chatModel",String.class,String.class,String.class,String.class);
        assertThatThrownBy(() -> method.invoke(config,"openai","","https://example.invalid/v1","example"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> method.invoke(config,"unknown","","",""))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(method.invoke(config,"offline","","",""))
                .isInstanceOf(dev.langchain4j.model.chat.ChatModel.class);
    }
}
