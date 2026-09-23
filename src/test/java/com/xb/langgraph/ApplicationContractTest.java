package com.xb.langgraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ApplicationContractTest {
    record Input(int score, boolean approved) {}
    record TextInput(String name) {}
    @Test void bootRegistersEighteenLessonsAndStrictJsonWithoutStartingServer() throws Exception {
        String name="com.xb.langgraph.LangGraphApplication";
        assertThatCode(() -> Class.forName(name)).doesNotThrowAnyException();
        var app = new SpringApplication(Class.forName(name));
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context=app.run("--teaching.model.provider=offline")) {
            assertThat(context.getBeansOfType(GraphDemo.class)).hasSize(18);
            assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("8099");
            var mapper=context.getBean(ObjectMapper.class);
            assertThatThrownBy(() -> mapper.readValue("{\"score\":1,\"approved\":true,\"extra\":0}",Input.class)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> mapper.readValue("{\"score\":\"1\",\"approved\":true}",Input.class)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> mapper.readValue("{\"score\":1.5,\"approved\":true}",Input.class)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> mapper.readValue("{\"score\":1,\"approved\":\"true\"}",Input.class)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> mapper.readValue("{\"name\":123}",TextInput.class)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> mapper.readValue("{\"name\":true}",TextInput.class)).isInstanceOf(Exception.class);
            assertThat(mapper.readValue("{\"score\":1,\"approved\":true}",Input.class)).isEqualTo(new Input(1,true));
        }
    }
}
