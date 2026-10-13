package com.example.demo.controller;

import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE 流式输出接口
 * 底层用 StreamingChatModel + StreamingChatResponseHandler 回调，逐 token 推给客户端
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final ChatModelRegistry modelRegistry;
    /** 专门跑流式异步调用的线程池（避免占用 Tomcat 工作线程） */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final String SYSTEM_PROMPT = "你是一个友好的 AI 助手，请根据对话上下文自然地回复用户。";

    public StreamController(ChatModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    /**
     * GET /api/stream/chat?q=你好&sessionId=s1 — 浏览器直接 SSE 订阅
     * 响应 Content-Type: text/event-stream
     *
     * @return SseEmitter，Spring 会自动保持 HTTP 连接，onPartialResponse 触发 send()
     */
    @GetMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam("q") String question,
                           @RequestParam(value = "sessionId", required = false) String sessionId,
                           @RequestParam(value = "provider", required = false) String provider) {

        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        String resolvedProvider = modelRegistry.resolve(provider);
        StreamingChatModel streamModel = modelRegistry.getStreaming(resolvedProvider);

        log.info("SSE 请求 q=[{}] provider={} sessionId={}", question, resolvedProvider, sid);

        // 超时 3 分钟（模型首 token 慢时留足余地）
        SseEmitter emitter = new SseEmitter(180_000L);

        // 构建消息：System + User（流式场景暂不接入记忆，后续可扩展）
        List<ChatMessage> messages = List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(question)
        );
        ChatRequest request = ChatRequest.builder().messages(messages).build();

        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("meta")
                        .data("provider=" + resolvedProvider + " sessionId=" + sid,
                                MediaType.TEXT_PLAIN));

                streamModel.chat(request, new StreamingChatResponseHandler() {

                    @Override
                    public void onPartialResponse(String partialResponse) {
                        try {
                            // 每收到一段增量文本就推给客户端（通常 1~2 个 token）
                            emitter.send(SseEmitter.event()
                                    .name("delta")
                                    .data(Map.of("content", partialResponse),
                                            MediaType.APPLICATION_JSON));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        // 模型结束，推 final 事件后关闭连接
                        try {
                            AiMessage finalMsg = completeResponse.aiMessage();
                            log.info("SSE 完成 sessionId={} finishReason={} 字符数={}",
                                    sid,
                                    completeResponse.finishReason() != null
                                            ? completeResponse.finishReason().name() : "unknown",
                                    finalMsg.text() != null ? finalMsg.text().length() : 0);
                            emitter.send(SseEmitter.event()
                                    .name("final")
                                    .data(Map.of(
                                            "content", finalMsg.text(),
                                            "finishReason", completeResponse.finishReason() != null
                                                    ? completeResponse.finishReason().name() : "unknown"),
                                            MediaType.APPLICATION_JSON));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("SSE 流式调用失败 sessionId={} provider={}", sid, resolvedProvider, error);
                        emitter.completeWithError(error);
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        // 客户端断开时的清理钩子
        emitter.onCompletion(() -> {});
        emitter.onTimeout(() -> emitter.complete());
        emitter.onError(t -> emitter.completeWithError(t));

        return emitter;
    }

    /** provider 不可用等参数错误，返回 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
