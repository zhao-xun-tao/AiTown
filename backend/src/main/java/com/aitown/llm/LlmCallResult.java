package com.aitown.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次 LLM 调用结果，携带研究可观测性字段（耗时 / Token）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmCallResult {

    private String content;

    private long latencyMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private String model;
}
