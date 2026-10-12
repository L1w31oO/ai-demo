package com.example.demo.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * 输入护栏：个人隐私信息（PII）脱敏
 * 手机号/身份证号/邮箱在发送给大模型前打码（successWith 改写用户消息）
 */
public class PiiMaskGuardrail implements InputGuardrail {

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String original = userMessage.singleText();
        String masked = GuardrailTextRules.maskPii(original);
        if (!masked.equals(original)) {
            // 改写后的文本会替代原始用户消息进入模型上下文
            return successWith(masked);
        }
        return success();
    }
}
