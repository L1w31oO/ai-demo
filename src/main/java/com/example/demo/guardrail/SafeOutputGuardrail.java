package com.example.demo.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * 输出护栏：敏感内容拦截 + PII 脱敏
 * 模型回复先经过检查：含敏感词 → failure 拦截；含 PII → 脱敏后返回给调用方
 */
public class SafeOutputGuardrail implements OutputGuardrail {

    @Override
    public OutputGuardrailResult validate(AiMessage aiMessage) {
        String original = aiMessage.text();

        String blockedWord = GuardrailTextRules.findBlockedWord(original);
        if (blockedWord != null) {
            return failure("模型输出包含敏感内容（" + blockedWord + "），已被护栏拦截");
        }

        String masked = GuardrailTextRules.maskPii(original);
        if (!masked.equals(original)) {
            return successWith(masked);
        }
        return success();
    }
}
