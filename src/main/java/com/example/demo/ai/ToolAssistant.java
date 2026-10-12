package com.example.demo.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 工具调用 AiService 接口
 * 与普通 Assistant 的区别：构建时通过 AiServices.tools() 绑定了工具，
 * 模型可自主决定调用（如天气查询）
 */
@SystemMessage("你是一个智能助手，可以调用工具查询实时信息。回答天气问题时请使用工具查询结果。")
public interface ToolAssistant {

    @UserMessage("{{it}}")
    String chat(String userMessage);
}
