package com.example.demo.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆管理：按 sessionId 维护多轮对话历史，滑动窗口限制上下文长度
 */
@Service
public class ChatMemoryService {

    /** 每个会话最多保留的消息条数（含 user + ai），超出时丢弃最早的消息 */
    private static final int MAX_MESSAGES = 20;

    private final InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
    private final ConcurrentHashMap<String, Object> knownSessions = new ConcurrentHashMap<>();

    /** 获取指定会话的全部历史消息 */
    public List<ChatMessage> getMessages(String sessionId) {
        List<ChatMessage> msgs = store.getMessages(sessionId);
        return msgs != null ? msgs : new ArrayList<>();
    }

    /** 追加用户消息并维护滑动窗口 */
    public void addUserMessage(String sessionId, String text) {
        knownSessions.putIfAbsent(sessionId, new Object());
        List<ChatMessage> messages = new ArrayList<>(getMessages(sessionId));
        messages.add(UserMessage.from(text));
        trim(messages);
        store.updateMessages(sessionId, messages);
    }

    /** 追加 AI 回复并维护滑动窗口 */
    public void addAiMessage(String sessionId, String text) {
        List<ChatMessage> messages = new ArrayList<>(getMessages(sessionId));
        messages.add(AiMessage.from(text));
        trim(messages);
        store.updateMessages(sessionId, messages);
    }

    /** 清空指定会话的历史 */
    public void clear(String sessionId) {
        store.deleteMessages(sessionId);
        knownSessions.remove(sessionId);
    }

    /** 清空所有会话 */
    public void clearAll() {
        knownSessions.keySet().forEach(store::deleteMessages);
        knownSessions.clear();
    }

    private void trim(List<ChatMessage> messages) {
        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }
}
