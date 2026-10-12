package com.example.demo.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AiService 接口：声明式定义大模型对话行为
 * LangChain4j 会自动为它生成代理实现类（AiServices.create/build）
 */
@SystemMessage("你是一个友好的 AI 助手，请根据对话上下文自然地回复用户。")
public interface Assistant {

    /** 最简单的对话方法：输入用户消息，返回 AI 回复 */
    @UserMessage("{{it}}")
    String chat(String userMessage);

    @UserMessage("把下面这段话翻译成{{language}}，只输出译文：\n{{text}}")
    String translate(@V("language") String language, @V("text") String text);
}
