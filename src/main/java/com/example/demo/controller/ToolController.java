package com.example.demo.controller;

import com.example.demo.ai.ToolAssistantFactory;
import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.guardrail.GuardrailException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工具调用（Tool Calling）对话接口
 * 模型可自主调用 WeatherTool 等工具获取实时数据后再作答
 */
@RestController
@RequestMapping("/api/tool")
public class ToolController {

    private final ToolAssistantFactory assistantFactory;
    private final ChatModelRegistry modelRegistry;

    public ToolController(ToolAssistantFactory assistantFactory, ChatModelRegistry modelRegistry) {
        this.assistantFactory = assistantFactory;
        this.modelRegistry = modelRegistry;
    }

    // ---- 请求 / 响应 DTO ----

    public record ChatRequestDto(String sessionId, String provider, String message) {
    }

    /**
     * POST /api/tool/chat — 支持工具调用的多轮对话
     * 请求体：{"sessionId": "s1", "provider": "ollama", "message": "杭州今天天气怎么样？"}
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequestDto request) {
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? "default" : request.sessionId();
        String provider = modelRegistry.resolve(request.provider());
        String reply = assistantFactory.getAssistant(provider, sessionId).chat(request.message());
        return Map.of("provider", provider, "sessionId", sessionId, "reply", reply);
    }

    /**
     * GET /api/tool/chat?q=杭州天气&sessionId=s1 — 浏览器直接测试
     */
    @GetMapping("/chat")
    public Map<String, Object> chatGet(@RequestParam("q") String question,
                                       @RequestParam(value = "sessionId", required = false) String sessionId,
                                       @RequestParam(value = "provider", required = false) String provider) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        String p = modelRegistry.resolve(provider);
        String reply = assistantFactory.getAssistant(p, sid).chat(question);
        return Map.of("provider", p, "sessionId", sid, "reply", reply);
    }

    /** 未知 provider / 配置缺失时返回 400 并给出明确提示 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    /** 护栏拦截（输入/输出违规），返回 400 与拦截原因 */
    @ExceptionHandler(GuardrailException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleGuardrail(GuardrailException e) {
        return Map.of("error", e.getMessage(), "guardrail", "violation");
    }
}
