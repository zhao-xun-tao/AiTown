package com.aitown.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容大模型配置（application.yml → llm.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** 是否启用真实 LLM；false 时降级为 Mock 决策 */
    private boolean enabled = false;

    /** API 根路径，如 https://api.openai.com/v1 或国内兼容网关 */
    private String baseUrl = "https://api.openai.com/v1";

    /** API Key，建议用环境变量 LLM_API_KEY 注入 */
    private String apiKey = "";

    /** 模型名 */
    private String model = "gpt-4o-mini";

    /** 单次请求超时（毫秒） */
    private int timeoutMs = 15000;

    private double temperature = 0.7;

    private int maxTokens = 512;

    /** 感知附近 Agent 的曼哈顿距离阈值 */
    private int perceptionRadius = 3;
}
