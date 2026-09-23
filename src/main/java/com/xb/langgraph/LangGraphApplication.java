package com.xb.langgraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;

@SpringBootApplication
public class LangGraphApplication {
    public static void main(String[] args) {
        SpringApplication.run(LangGraphApplication.class,args);
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictTextScalars() {
        // ALLOW_COERCION_OF_SCALARS=false 仍允许 number/boolean -> String，需单独关闭。
        return builder -> builder.postConfigurer(mapper -> {
            var text = mapper.coercionConfigFor(LogicalType.Textual);
            text.setCoercion(CoercionInputShape.Integer,CoercionAction.Fail);
            text.setCoercion(CoercionInputShape.Float,CoercionAction.Fail);
            text.setCoercion(CoercionInputShape.Boolean,CoercionAction.Fail);
        });
    }
}
