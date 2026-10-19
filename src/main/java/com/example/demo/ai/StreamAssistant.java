package com.example.demo.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 流式 AiService 接口：方法返回 TokenStream，LangChain4j 自动用 StreamingChatModel 驱动
 * 调用方通过链式 onPartialResponse / onCompleteResponse / onError + start() 消费流
 */
@SystemMessage("你是一个友好的 AI 助手，请根据对话上下文自然地回复用户。")
public interface StreamAssistant {

    @UserMessage("{{it}}")
    TokenStream chat(String userMessage);
}
