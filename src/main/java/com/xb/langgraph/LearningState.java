package com.xb.langgraph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import java.util.*;

/** 共享教学状态；节点只返回增量，合并由原生 Channel 完成。 */
public class LearningState extends AgentState {
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "sum", Channels.base(Integer::sum, () -> 0),
            "events", Channels.appender(ArrayList::new),
            "trace", Channels.appenderWithDuplicate(ArrayList::new),
            "facts", Channels.appender(ArrayList::new),
            "messages", Channels.appenderWithDuplicate(ArrayList::new),
            "appliedOperations", Channels.appender(ArrayList::new),
            "timingsNanos", Channels.<Map<String,Long>>base((oldValue, update) -> {
                var merged = new LinkedHashMap<>(oldValue);
                merged.putAll(update);
                return merged;
            }, LinkedHashMap::new));

    public LearningState(Map<String,Object> data) { super(data); }

    public String text(String key) {
        Object value = value(key).orElse(null);
        if (!(value instanceof String text))
            throw new IllegalArgumentException(key + " 必须为字符串");
        return text;
    }
    public String inputText(String key) {
        String text = text(key);
        if (text.isBlank() || text.length() > 2000)
            throw new IllegalArgumentException(key + " 必须为1到2000字符的非空字符串");
        return text.strip();
    }
    public int integer(String key, int min, int max) {
        Object value = value(key).orElse(null);
        if (!(value instanceof Integer number) || number < min || number > max)
            throw new IllegalArgumentException(key + " 必须为整数，范围 " + min + ".." + max);
        return number;
    }
    public boolean decision(String key) {
        Object value = value(key).orElse(null);
        if (!(value instanceof Boolean approved)) throw new IllegalArgumentException(key + " 必须为布尔审核决定");
        return approved;
    }
    public int count(String key) { return this.<Integer>value(key).orElse(0); }
    public <T> List<T> list(String key) { return this.<List<T>>value(key).orElseGet(List::of); }
}
