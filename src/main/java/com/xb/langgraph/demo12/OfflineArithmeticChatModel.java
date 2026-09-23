package com.xb.langgraph.demo12;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.regex.Pattern;

/** 真正实现 LangChain4j ChatModel 协议的确定性离线教学模型，不冒充远程大模型。 */
public class OfflineArithmeticChatModel implements ChatModel {
    private static final Pattern INPUT = Pattern.compile("请调用 add 计算 (-?\\d+) \\+ (-?\\d+)");
    @Override public ChatResponse doChat(ChatRequest request) {
        var messages = request.messages();
        var last = messages.getLast();
        if (last instanceof ToolExecutionResultMessage result) {
            return ChatResponse.builder().aiMessage(AiMessage.from("离线工具结果：" + result.text())).build();
        }
        if (!(last instanceof UserMessage user)) throw new IllegalArgumentException("离线模型仅支持加法教学对话");
        var match = INPUT.matcher(user.singleText());
        if (!match.matches()) throw new IllegalArgumentException("离线模型输入格式错误");
        var tool = ToolExecutionRequest.builder().id("offline-add-1").name("add")
                .arguments("{\"a\":" + match.group(1) + ",\"b\":" + match.group(2) + "}").build();
        return ChatResponse.builder().aiMessage(AiMessage.from(tool)).build();
    }
}
