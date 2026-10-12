package com.example.demo.ai;

import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按 provider + sessionId 缓存 ToolAssistant 实例的工厂
 * 与普通对话共用相同的构建方式，额外通过 .tools() 绑定可用工具
 * 记忆独立存储（不与普通对话混用）
 */
@Component
public class ToolAssistantFactory {

    /** 每个会话最多保留的消息条数 */
    private static final int MAX_MESSAGES = 20;

    private final ChatModelRegistry modelRegistry;
    private final WeatherTool weatherTool;

    private final InMemoryChatMemoryStore memoryStore = new InMemoryChatMemoryStore();

    /** 缓存 key = "provider::sessionId" */
    private final ConcurrentMap<String, ToolAssistant> assistantCache = new ConcurrentHashMap<>();

    public ToolAssistantFactory(ChatModelRegistry modelRegistry, WeatherTool weatherTool) {
        this.modelRegistry = modelRegistry;
        this.weatherTool = weatherTool;
    }

    /**
     * 获取或创建指定 provider + sessionId 对应的 ToolAssistant 实例
     */
    public ToolAssistant getAssistant(String provider, String sessionId) {
        String key = provider + "::" + sessionId;
        return assistantCache.computeIfAbsent(key, k -> buildAssistant(provider, sessionId));
    }

    private ToolAssistant buildAssistant(String provider, String sessionId) {
        ChatModel model = modelRegistry.get(provider);

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id("tool::" + sessionId)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(memoryStore)
                .build();

        return AiServices.builder(ToolAssistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .tools(weatherTool)
                .build();
    }
}
