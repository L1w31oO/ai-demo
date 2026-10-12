package com.example.demo.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * RAG 问答 AiService：AiServices 自动完成 检索 → 上下文注入 → 调用大模型
 */
@SystemMessage("你是知识库问答助手。请仅根据提供的上下文回答用户问题；如果上下文中没有相关信息，请如实说明。")
public interface RagAssistant {

    @UserMessage("{{it}}")
    String chat(String userMessage);
}
