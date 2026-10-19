package com.example.demo.ai;

import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按 provider + sessionId 缓存 StreamAssistant 实例的工厂
 * 底层用 StreamingChatModel + AiServices 代理，共享 InMemoryChatMemoryStore 实现会话隔离
 */
@Slf4j
@Component
public class StreamAssistantFactory {

    private static final int MAX_MESSAGES = 20;

    private final ChatModelRegistry modelRegistry;

    /** 共享的底层记忆存储：按 memoryId 隔离，不同 sessionId 互不干扰 */
    private final InMemoryChatMemoryStore memoryStore = new InMemoryChatMemoryStore();

    /** 缓存 key = "provider::sessionId" */
    private final ConcurrentMap<String, StreamAssistant> cache = new ConcurrentHashMap<>();

    public StreamAssistantFactory(ChatModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public StreamAssistant get(String provider, String sessionId) {
        String key = provider + "::" + sessionId;
        return cache.computeIfAbsent(key, k -> build(provider, sessionId));
    }

    private StreamAssistant build(String provider, String sessionId) {
        StreamingChatModel model = modelRegistry.getStreaming(provider);

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(memoryStore)
                .build();

        log.info("构建 StreamAssistant provider={} sessionId={}", provider, sessionId);

        return AiServices.builder(StreamAssistant.class)
                .streamingChatModel(model)
                .chatMemory(chatMemory)
                .build();
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return memoryStore.getMessages(sessionId);
    }

    public void clearSession(String sessionId) {
        cache.keySet().removeIf(key -> key.endsWith("::" + sessionId));
        memoryStore.deleteMessages(sessionId);
    }
}
