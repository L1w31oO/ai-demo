package com.example.demo.controller;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大模型对话接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatModel chatModel;

    public ChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String reply) {
    }

    /**
     * POST /api/chat
     * 请求体：{"message": "你好"}
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = chatModel.chat(request.message());
        return new ChatResponse(reply);
    }

    /**
     * GET /api/chat?q=你好 （方便浏览器直接访问测试）
     */
    @GetMapping
    public ChatResponse chatByGet(@RequestParam("q") String question) {
        String reply = chatModel.chat(question);
        return new ChatResponse(reply);
    }
}
