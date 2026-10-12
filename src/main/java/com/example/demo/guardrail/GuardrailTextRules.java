package com.example.demo.guardrail;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 护栏共享规则：敏感词黑名单、提示词注入模式、PII 脱敏
 * 演示用硬编码；生产环境建议外部化为配置或接入内容安全审核服务
 */
final class GuardrailTextRules {

    /** 敏感词黑名单 */
    static final List<String> BLOCKED_WORDS = List.of("炸弹", "毒品", "枪支");

    /** 常见提示词注入模式 */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("忽略(之前|上面|先前|以上)的?(所有|全部)?指令"),
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions"),
            Pattern.compile("(你的|泄露|打印|输出).{0,6}(系统提示词?|system prompt)"),
            Pattern.compile("(?i)reveal\\s+(your\\s+)?system\\s+prompt")
    );

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{4})\\d{10}(\\d{3}[\\dXx])(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-]{2})[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+)");

    private GuardrailTextRules() {
    }

    /** 返回命中的敏感词，未命中返回 null */
    static String findBlockedWord(String text) {
        for (String word : BLOCKED_WORDS) {
            if (text.contains(word)) {
                return word;
            }
        }
        return null;
    }

    /** 是否命中提示词注入模式 */
    static boolean isPromptInjection(String text) {
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /** PII 脱敏：手机号、身份证号、邮箱打码 */
    static String maskPii(String text) {
        String masked = PHONE.matcher(text).replaceAll("$1****$2");
        masked = ID_CARD.matcher(masked).replaceAll("$1**********$2");
        masked = EMAIL.matcher(masked).replaceAll("$1****$2");
        return masked;
    }
}
