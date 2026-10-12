package com.example.demo.service;

import dev.langchain4j.model.chat.ChatModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型注册表：按 provider 名称管理本地/远程大模型实例，支持运行时切换
 */
public class ChatModelRegistry {

    private final Map<String, ChatModel> models;
    /** provider -> 展示描述（模型名） */
    private final Map<String, String> descriptions;
    private final String defaultProvider;

    public ChatModelRegistry(Map<String, ChatModel> models,
                             Map<String, String> descriptions,
                             String defaultProvider) {
        this.models = new LinkedHashMap<>(models);
        this.descriptions = new LinkedHashMap<>(descriptions);
        this.defaultProvider = defaultProvider;
    }

    /**
     * 按 provider 获取模型；provider 为空时使用默认值
     *
     * @throws IllegalArgumentException provider 不可用（未配置或名称错误）
     */
    public ChatModel get(String provider) {
        String key = (provider == null || provider.isBlank())
                ? defaultProvider
                : provider.trim().toLowerCase();
        ChatModel model = models.get(key);
        if (model == null) {
            throw new IllegalArgumentException(
                    "模型 provider [" + key + "] 不可用（未配置 API Key 或名称错误）。当前可用: " + models.keySet());
        }
        return model;
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
