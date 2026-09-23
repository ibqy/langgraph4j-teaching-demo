package com.xb.langgraph.demo12;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.net.URI;
import java.time.Duration;

@Configuration
public class ModelConfiguration {
    @Bean
    public ChatModel chatModel(@Value("${teaching.model.provider:offline}") String provider,
                               @Value("${teaching.model.api-key:}") String apiKey,
                               @Value("${teaching.model.base-url:}") String baseUrl,
                               @Value("${teaching.model.model:}") String model) {
        if ("offline".equals(provider)) return new OfflineArithmeticChatModel();
        if (!"openai".equals(provider)) throw new IllegalArgumentException("provider 仅支持 offline 或 openai");
        if (apiKey.isBlank() || baseUrl.isBlank() || model.isBlank())
            throw new IllegalArgumentException("openai 模式需要 api-key、base-url、model；默认离线不需要密钥");
        var uri = URI.create(baseUrl);
        String host = uri.getHost();
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        if (host == null || uri.getUserInfo() != null ||
                !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback)))
            throw new IllegalArgumentException("base-url 必须为不含凭据的 HTTPS 地址；仅 localhost、127.0.0.1、[::1] 允许开发用 HTTP");
        return OpenAiChatModel.builder().apiKey(apiKey).baseUrl(baseUrl).modelName(model)
                .temperature(0.0).timeout(Duration.ofSeconds(30)).maxRetries(0)
                .logRequests(false).logResponses(false).build();
    }
}
