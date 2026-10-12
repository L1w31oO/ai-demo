package com.example.demo.controller;

import com.example.demo.ai.SessionAssistantFactory;
import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
 * 大模型对话接口（基于 LangChain4j AiServices 高层 API）
 * 支持多 provider 切换 + 按 sessionId 自动记忆管理
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final SessionAssistantFactory assistantFactory;
    private final ChatModelRegistry modelRegistry;

    public ChatController(SessionAssistantFactory assistantFactory, ChatModelRegistry modelRegistry) {
        this.assistantFactory = assistantFactory;
        this.modelRegistry = modelRegistry;
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

        // 从工厂获取 Assistant 代理实例（自动携带该会话的 ChatMemory）
        var assistant = assistantFactory.getAssistant(provider, sessionId);

        // 直接调用 AiService 接口方法，LangChain4j 自动处理：
        // SystemMessage 注入 → ChatMemory 历史拼接 → 调用 ChatModel → 返回 String
        String reply = assistant.chat(request.message());

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
     * GET /api/chat/history/{sessionId} — 查看指定会话的对话历史
     * 返回的即模型实际收到的上下文（system 提示词 + 用户消息 + AI 回复）
     */
    @GetMapping("/history/{sessionId}")
    public List<HistoryItem> history(@PathVariable String sessionId) {
        List<HistoryItem> result = new ArrayList<>();
        for (ChatMessage msg : assistantFactory.getHistory(sessionId)) {
            if (msg instanceof SystemMessage sm) {
                result.add(new HistoryItem("system", sm.text()));
            } else if (msg instanceof UserMessage um) {
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
        assistantFactory.clearSession(sessionId);
        return Map.of("status", "cleared", "sessionId", sessionId);
    }

    /**
     * DELETE /api/chat/history — 清空所有会话
     */
    @DeleteMapping("/history")
    public Map<String, String> clearAllHistory() {
        assistantFactory.clearAll();
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
