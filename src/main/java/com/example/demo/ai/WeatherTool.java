package com.example.demo.ai;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 天气查询工具（模拟数据）
 * LangChain4j 会把 @Tool 方法注册为模型的工具（function calling），
 * 模型判断需要时自动发起调用，由框架执行后把结果回传给模型继续作答
 */
@Component
public class WeatherTool {

    @Tool("查询指定城市当前的天气情况")
    public String weather(@P("城市名称，例如：杭州") String city) {
        // 模拟数据：真实场景可替换为高德/和风天气等天气 API 调用
        return switch (city) {
            case "北京" -> "多云，18°C，湿度 40%，北风 3 级";
            case "上海" -> "小雨，20°C，湿度 85%，东风 2 级";
            case "杭州" -> "晴，24°C，湿度 60%，东南风 2 级";
            default -> "晴，22°C，湿度 50%，微风";
        };
    }
}
