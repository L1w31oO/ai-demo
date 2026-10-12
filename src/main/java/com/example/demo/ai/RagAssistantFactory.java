package com.example.demo.ai;

import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按 provider 缓存 RagAssistant 实例（RAG 问答为无状态调用，不做会话记忆）
 */
@Component
public class RagAssistantFactory {

    private final ChatModelRegistry modelRegistry;
    private final ContentRetriever contentRetriever;
    private final ConcurrentMap<String, RagAssistant> cache = new ConcurrentHashMap<>();

    public RagAssistantFactory(ChatModelRegistry modelRegistry, ContentRetriever contentRetriever) {
        this.modelRegistry = modelRegistry;
        this.contentRetriever = contentRetriever;
    }

    /**
     * 获取或创建指定 provider 的 RAG 助手（自动挂载内容检索器）
     */
    public RagAssistant get(String provider) {
        String key = modelRegistry.resolve(provider);
        return cache.computeIfAbsent(key, k -> AiServices.builder(RagAssistant.class)
                .chatModel(modelRegistry.get(k))
                .contentRetriever(contentRetriever)
                .build());
    }
}
