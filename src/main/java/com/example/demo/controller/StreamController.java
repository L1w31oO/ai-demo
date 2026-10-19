package com.example.demo.controller;

import com.example.demo.ai.StreamAssistant;
import com.example.demo.ai.StreamAssistantFactory;
import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE 流式输出接口（基于 AiServices + TokenStream）
 * 相比直调 StreamingChatModel：自动获得 SystemMessage 注入 + ChatMemory 记忆管理
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final StreamAssistantFactory assistantFactory;
    private final ChatModelRegistry modelRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public StreamController(StreamAssistantFactory assistantFactory,
                           ChatModelRegistry modelRegistry) {
        this.assistantFactory = assistantFactory;
        this.modelRegistry = modelRegistry;
    }

    @GetMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam("q") String question,
                           @RequestParam(value = "sessionId", required = false) String sessionId,
                           @RequestParam(value = "provider", required = false) String provider) {

        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        String resolvedProvider = modelRegistry.resolve(provider);

        log.info("SSE 请求 q=[{}] provider={} sessionId={}", question, resolvedProvider, sid);

        SseEmitter emitter = new SseEmitter(180_000L);

        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("meta")
                        .data("provider=" + resolvedProvider + " sessionId=" + sid,
                                MediaType.TEXT_PLAIN));

                StreamAssistant assistant = assistantFactory.get(resolvedProvider, sid);
                TokenStream stream = assistant.chat(question);

                stream
                    .onPartialResponse(token -> {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("delta")
                                    .data(Map.of("content", token),
                                            MediaType.APPLICATION_JSON));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onCompleteResponse(response -> {
                        try {
                            log.info("SSE 完成 sessionId={} finishReason={} 字符数={}",
                                    sid,
                                    response.finishReason() != null
                                            ? response.finishReason().name() : "unknown",
                                    response.aiMessage().text() != null
                                            ? response.aiMessage().text().length() : 0);
                            emitter.send(SseEmitter.event()
                                    .name("final")
                                    .data(Map.of(
                                            "content", response.aiMessage().text(),
                                            "finishReason", response.finishReason() != null
                                                    ? response.finishReason().name() : "unknown"),
                                            MediaType.APPLICATION_JSON));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onError(error -> {
                        log.error("SSE 流式调用失败 sessionId={} provider={}", sid, resolvedProvider, error);
                        emitter.completeWithError(error);
                    })
                    .start();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> emitter.complete());
        emitter.onError(t -> emitter.completeWithError(t));

        return emitter;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
