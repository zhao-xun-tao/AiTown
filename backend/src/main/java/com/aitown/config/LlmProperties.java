package com.aitown.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型配置（application.yml → llm.*）。
 * 默认对接阿里百炼 DashScope OpenAI 兼容接口。
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** 是否启用真实 LLM；false 时降级为 Mock 决策 */
    private boolean enabled = false;

    /** API 根路径：百炼兼容模式默认如下 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** API Key，建议用环境变量 DASHSCOPE_API_KEY 注入 */
    private String apiKey = "";

    /** 模型名：qwen-turbo / qwen-plus / qwen-max 等 */
    private String model = "qwen-plus";

    /** 单次请求超时（毫秒） */
    private int timeoutMs = 15000;

    private double temperature = 0.7;

    private int maxTokens = 512;

    /** 感知附近 Agent 的曼哈顿距离阈值 */
    private int perceptionRadius = 3;
}
