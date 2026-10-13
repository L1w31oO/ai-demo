package com.example.demo.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型注册表：按 provider 名称管理本地/远程大模型实例，支持运行时切换
 * 同时维护同步 ChatModel 和流式 StreamingChatModel 两套实例
 */
@Slf4j
public class ChatModelRegistry {

    private final Map<String, ChatModel> models;
    private final Map<String, StreamingChatModel> streamingModels;
    /** provider -> 展示描述（模型名） */
    private final Map<String, String> descriptions;
    private final String defaultProvider;

    public ChatModelRegistry(Map<String, ChatModel> models,
                             Map<String, StreamingChatModel> streamingModels,
                             Map<String, String> descriptions,
                             String defaultProvider) {
        this.models = new LinkedHashMap<>(models);
        this.streamingModels = new LinkedHashMap<>(streamingModels);
        this.descriptions = new LinkedHashMap<>(descriptions);
        this.defaultProvider = defaultProvider;
        log.info("已注册 {} 个 provider: {}（默认={}）",
                models.size(), descriptions.keySet(), defaultProvider);
    }

    /**
     * 按 provider 获取同步模型；provider 为空时使用默认值
     *
     * @throws IllegalArgumentException provider 不可用（未配置或名称错误）
     */
    public ChatModel get(String provider) {
        String key = resolve(provider);
        ChatModel model = models.get(key);
        if (model == null) {
            throw notFound(key);
        }
        return model;
    }

    /**
     * 按 provider 获取流式模型；provider 为空时使用默认值
     *
     * @throws IllegalArgumentException provider 不可用
     */
    public StreamingChatModel getStreaming(String provider) {
        String key = resolve(provider);
        StreamingChatModel model = streamingModels.get(key);
        if (model == null) {
            throw notFound(key);
        }
        return model;
    }

    private IllegalArgumentException notFound(String provider) {
        return new IllegalArgumentException(
                "模型 provider [" + provider + "] 不可用（未配置 API Key 或名称错误）。当前可用: "
                        + models.keySet());
    }

    /** 解析实际生效的 provider 名称 */
    public String resolve(String provider) {
        return (provider == null || provider.isBlank()) ? defaultProvider : provider.trim().toLowerCase();
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    /** 当前可用的 provider 及其模型描述 */
    public Map<String, String> availableProviders() {
        return Collections.unmodifiableMap(descriptions);
    }
}
