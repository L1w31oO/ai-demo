package com.example.demo.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * 输入护栏：敏感词拦截 + 提示词注入检测
 * 命中即返回 failure，请求不会发送给大模型（抛出 InputGuardrailException）
 */
public class SensitiveInputGuardrail implements InputGuardrail {

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText();

        String blockedWord = GuardrailTextRules.findBlockedWord(text);
        if (blockedWord != null) {
            return failure("输入包含敏感词「" + blockedWord + "」，已被护栏拦截");
        }
        if (GuardrailTextRules.isPromptInjection(text)) {
            return failure("检测到疑似提示词注入攻击，已被护栏拦截");
        }
        return success();
    }
}
