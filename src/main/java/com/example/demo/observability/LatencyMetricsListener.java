package com.example.demo.observability;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 调用耗时指标监听器：记录 gen_ai.client.operation.duration（OTel GenAI 语义规范）
 * 官方 MicrometerMetricsChatModelListener（1.22.0-beta32）仅记录 token 用量，
 * 耗时指标由此监听器补充，指标名与标签和官方约定保持一致
 */
public class LatencyMetricsListener implements ChatModelListener {

    public static final String METRIC_NAME = "gen_ai.client.operation.duration";
    private static final String START_TIME_KEY = "llm.request.start";

    private final MeterRegistry meterRegistry;
    private final String providerName;

    public LatencyMetricsListener(MeterRegistry meterRegistry, String providerName) {
        this.meterRegistry = meterRegistry;
        this.providerName = providerName;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(START_TIME_KEY, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Long start = (Long) responseContext.attributes().get(START_TIME_KEY);
        if (start != null) {
            recordDuration(start, responseContext.chatRequest().modelName(), null);
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Long start = (Long) errorContext.attributes().get(START_TIME_KEY);
        if (start != null) {
            recordDuration(start, errorContext.chatRequest().modelName(),
                    errorContext.error().getClass().getSimpleName());
        }
    }

    private void recordDuration(long startNanos, String modelName, String errorType) {
        Timer.Builder timer = Timer.builder(METRIC_NAME)
                .description("GenAI operation duration")
                .tag("gen_ai.operation.name", "chat")
                .tag("gen_ai.provider.name", providerName)
                .tag("gen_ai.request.model", StringUtils.hasText(modelName) ? modelName : "unknown");
        if (errorType != null) {
            timer.tag("error.type", errorType);
        }
        timer.register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
}
