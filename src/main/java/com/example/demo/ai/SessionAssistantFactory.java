package com.example.demo.ai;

import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按 provider + sessionId 缓存 Assistant 实例的工厂
 * 所有 ChatMemory 共享同一个 InMemoryChatMemoryStore（按 memoryId 天然隔离）
 */
@Component
public class SessionAssistantFactory {

    /** 每个会话最多保留的消息条数 */
    private static final int MAX_MESSAGES = 20;

    private final ChatModelRegistry modelRegistry;

    /** 共享的底层记忆存储：按 memoryId 隔离，不同 sessionId 互不干扰 */
    private final InMemoryChatMemoryStore memoryStore = new InMemoryChatMemoryStore();

    /** 缓存 key = "provider::sessionId" */
    private final ConcurrentMap<String, Assistant> assistantCache = new ConcurrentHashMap<>();

    public SessionAssistantFactory(ChatModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    /**
     * 获取或创建指定 provider + sessionId 对应的 Assistant 实例
     */
    public Assistant getAssistant(String provider, String sessionId) {
        String key = provider + "::" + sessionId;
        return assistantCache.computeIfAbsent(key, k -> buildAssistant(provider, sessionId));
    }

    private Assistant buildAssistant(String provider, String sessionId) {
        ChatModel model = modelRegistry.get(provider);

        // 共享同一个 memoryStore，按 id 隔离不同会话
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(memoryStore)
                .build();

        return AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .build();
    }

    /**
     * 查看指定会话的对话历史（即发送给模型的上下文，含 system/user/assistant 消息）
     */
    public List<ChatMessage> getHistory(String sessionId) {
        return memoryStore.getMessages(sessionId);
    }

    /**
     * 清空指定会话的记忆（同一 sessionId 的所有 provider 共享同一份记忆）
     */
    public void clearSession(String sessionId) {
        assistantCache.keySet().removeIf(key -> key.endsWith("::" + sessionId));
        memoryStore.deleteMessages(sessionId);
    }

    /** 清空所有会话记忆 */
    public void clearAll() {
        // 注意：必须先按已知 sessionId 删除存储的消息，再清空缓存（顺序反了会导致删不掉）
        assistantCache.keySet().forEach(key -> {
            String sessionId = key.substring(key.indexOf("::") + 2);
            memoryStore.deleteMessages(sessionId);
        });
        assistantCache.clear();
    }
}
