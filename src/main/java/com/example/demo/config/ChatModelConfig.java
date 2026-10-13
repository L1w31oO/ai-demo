package com.example.demo.config;

import com.example.demo.observability.LatencyMetricsListener;
import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.micrometer.metrics.listeners.MicrometerMetricsChatModelListener;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型配置：Ollama 本地模型 + DeepSeek / Qwen / OpenAI 远程 API
 * 远程 provider 仅在对应 API Key 已配置时注册
 * 每个 provider 同时构建 同步 ChatModel + 流式 StreamingChatModel 两套实例
 */
@Configuration
public class ChatModelConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final double TEMPERATURE = 0.7;

    /** Micrometer 指标监听器：记录调用耗时 / token 用量 / 错误（OTel gen_ai.* 指标） */
    private final MicrometerMetricsChatModelListener metricsListener;
    private final MeterRegistry meterRegistry;

    public ChatModelConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.metricsListener = new MicrometerMetricsChatModelListener(meterRegistry);
    }

    /** 每个模型挂载：官方 token 指标监听器 + 自定义耗时指标监听器 */
    private List<ChatModelListener> listeners(String provider) {
        return List.of(metricsListener, new LatencyMetricsListener(meterRegistry, provider));
    }

    /** 多模型注册表：Ollama + 远程 API（按需注册），同时构建同步 + 流式两套实例 */
    @Bean
    public ChatModelRegistry chatModelRegistry(
            @Value("${chat.default-provider:ollama}") String defaultProvider,

            @Value("${ollama.base-url}") String ollamaBaseUrl,
            @Value("${ollama.model-name}") String ollamaModel,

            @Value("${deepseek.api-key:}") String deepseekKey,
            @Value("${deepseek.base-url:https://api.deepseek.com/v1}") String deepseekBaseUrl,
            @Value("${deepseek.model-name:deepseek-chat}") String deepseekModel,

            @Value("${qwen.api-key:}") String qwenKey,
            @Value("${qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String qwenBaseUrl,
            @Value("${qwen.model-name:qwen-plus}") String qwenModel,

            @Value("${openai.api-key:}") String openaiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String openaiBaseUrl,
            @Value("${openai.model-name:gpt-4o-mini}") String openaiModel) {

        Map<String, ChatModel> models = new LinkedHashMap<>();
        Map<String, StreamingChatModel> streamingModels = new LinkedHashMap<>();
        Map<String, String> descriptions = new LinkedHashMap<>();

        // ---- 本地 Ollama（始终可用）----
        models.put("ollama", OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .temperature(TEMPERATURE)
                .timeout(TIMEOUT)
                .listeners(listeners("ollama"))
                .build());
        streamingModels.put("ollama", OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .temperature(TEMPERATURE)
                .timeout(TIMEOUT)
                .listeners(listeners("ollama"))
                .build());
        descriptions.put("ollama", "Ollama 本地模型（" + ollamaModel + "）");

        // ---- DeepSeek 远程 API（OpenAI 兼容）----
        if (StringUtils.hasText(deepseekKey)) {
            models.put("deepseek", OpenAiChatModel.builder()
                    .baseUrl(deepseekBaseUrl).apiKey(deepseekKey).modelName(deepseekModel)
                    .temperature(TEMPERATURE).timeout(TIMEOUT)
                    .listeners(listeners("deepseek")).build());
            streamingModels.put("deepseek", OpenAiStreamingChatModel.builder()
                    .baseUrl(deepseekBaseUrl).apiKey(deepseekKey).modelName(deepseekModel)
                    .temperature(TEMPERATURE).timeout(TIMEOUT)
                    .listeners(listeners("deepseek")).build());
            descriptions.put("deepseek", "DeepSeek API（" + deepseekModel + "）");
        }

        // ---- 通义千问远程 API（DashScope OpenAI 兼容模式）----
        if (StringUtils.hasText(qwenKey)) {
            models.put("qwen", OpenAiChatModel.builder()
                    .baseUrl(qwenBaseUrl).apiKey(qwenKey).modelName(qwenModel)
                    .temperature(TEMPERATURE).timeout(TIMEOUT)
                    .listeners(listeners("qwen")).build());
            streamingModels.put("qwen", OpenAiStreamingChatModel.builder()
                    .baseUrl(qwenBaseUrl).apiKey(qwenKey).modelName(qwenModel)
                    .temperature(TEMPERATURE).timeout(TIMEOUT)
                    .listeners(listeners("qwen")).build());
            descriptions.put("qwen", "通义千问 API（" + qwenModel + "）");
        }

        // ---- OpenAI ChatGPT 远程 API ----
        if (StringUtils.hasText(openaiKey)) {
            models.put("openai", OpenAiChatModel.builder()
                    .baseUrl(openaiBaseUrl).apiKey(openaiKey).modelName(openaiModel)
                    .temperature(TEMPERATURE).timeout(TIMEOUT)
                    .listeners(listeners("openai")).build());
            streamingModels.put("openai", OpenAiStreamingChatModel.builder()
                    .baseUrl(openaiBaseUrl).apiKey(openaiKey).modelName(openaiModel)
                    .temperature(TEMPERATURE).timeout(TIMEOUT)
                    .listeners(listeners("openai")).build());
            descriptions.put("openai", "OpenAI ChatGPT API（" + openaiModel + "）");
        }

        return new ChatModelRegistry(models, streamingModels, descriptions, defaultProvider);
    }
}
