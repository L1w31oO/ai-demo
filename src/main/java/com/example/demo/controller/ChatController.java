package com.example.demo.controller;

import com.example.demo.service.ChatModelRegistry;
import com.example.demo.service.ChatMemoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 大模型对话接口（多轮记忆 + 多 provider 切换）
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatModelRegistry modelRegistry;
    private final ChatMemoryService memoryService;

    private static final String SYSTEM_PROMPT =
            "你是一个友好的 AI 助手，请根据对话上下文自然地回复用户。";

    public ChatController(ChatModelRegistry modelRegistry, ChatMemoryService memoryService) {
        this.modelRegistry = modelRegistry;
        this.memoryService = memoryService;
    }

    // ---- 请求 / 响应 DTO ----

    public record ChatRequestDto(String sessionId, String provider, String message) {
    }

    public record ChatResponseDto(String sessionId, String provider, String reply) {
    }

    public record HistoryItem(String role, String content) {
    }

    // ---- 多轮对话（带记忆，可指定 provider）----

    /**
     * POST /api/chat
     * 请求体：{"sessionId": "s1", "provider": "deepseek", "message": "你好"}
     * provider 可选：ollama / deepseek / qwen / openai，缺省用默认 provider
     */
    @PostMapping
    public ChatResponseDto chat(@RequestBody ChatRequestDto request) {
        String sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                ? request.sessionId()
                : "default";
        String provider = modelRegistry.resolve(request.provider());
        ChatModel chatModel = modelRegistry.get(provider);

        // 1. 将用户消息写入记忆
        memoryService.addUserMessage(sessionId, request.message());

        // 2. 组装带历史的请求（System prompt + 会话历史）
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        messages.addAll(memoryService.getMessages(sessionId));

        ChatRequest chatReq = ChatRequest.builder()
                .messages(messages)
                .build();

        // 3. 调用大模型
        ChatResponse response = chatModel.chat(chatReq);
        String reply = response.aiMessage().text();

        // 4. 将 AI 回复写入记忆
        memoryService.addAiMessage(sessionId, reply);

        return new ChatResponseDto(sessionId, provider, reply);
    }

    /**
     * GET /api/chat?q=你好&sessionId=s1&provider=deepseek （方便浏览器直接测试）
     */
    @GetMapping
    public ChatResponseDto chatByGet(
            @RequestParam("q") String question,
            @RequestParam(value = "sessionId", required = false, defaultValue = "default") String sessionId,
            @RequestParam(value = "provider", required = false) String provider) {
        return chat(new ChatRequestDto(sessionId, provider, question));
    }

    // ---- 模型 / 会话管理 ----

    /**
     * GET /api/chat/providers — 查看当前可用的模型 provider
     */
    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return Map.of(
                "default", modelRegistry.getDefaultProvider(),
                "available", modelRegistry.availableProviders()
        );
    }

    /**
     * GET /api/chat/history/{sessionId} — 查看会话历史
     */
    @GetMapping("/history/{sessionId}")
    public List<HistoryItem> getHistory(@PathVariable String sessionId) {
        List<HistoryItem> result = new ArrayList<>();
        for (ChatMessage msg : memoryService.getMessages(sessionId)) {
            if (msg instanceof UserMessage um) {
                result.add(new HistoryItem("user", um.singleText()));
            } else if (msg instanceof AiMessage am) {
                result.add(new HistoryItem("assistant", am.text()));
            }
        }
        return result;
    }

    /**
     * DELETE /api/chat/history/{sessionId} — 清空指定会话
     */
    @DeleteMapping("/history/{sessionId}")
    public Map<String, String> clearHistory(@PathVariable String sessionId) {
        memoryService.clear(sessionId);
        return Map.of("status", "cleared", "sessionId", sessionId);
    }

    /**
     * DELETE /api/chat/history — 清空所有会话
     */
    @DeleteMapping("/history")
    public Map<String, String> clearAllHistory() {
        memoryService.clearAll();
        return Map.of("status", "all_cleared");
    }

    // ---- 异常处理 ----

    /** provider 不可用等参数错误，返回 400 与可读信息 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
