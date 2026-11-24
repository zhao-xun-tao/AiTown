package com.aitown.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Generative Agents 双层记忆参数（application.yml → memory.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    private boolean enabled = true;

    /** Redis 短期工作记忆条数 */
    private int workingMemorySize = 10;

    /** 长期记忆检索 Top-K */
    private int retrieveTopK = 5;

    /** 打分前从 MySQL 拉取的候选数量 */
    private int retrieveCandidateLimit = 80;

    /** 累计重要性达到该阈值触发反思 */
    private int reflectionImportanceThreshold = 30;

    private double weightRecency = 1.0;

    private double weightImportance = 1.0;

    private double weightRelevance = 1.0;

    /**
     * 时间衰减系数：recency = decay^gameHoursAgo（接近论文 exponential decay）。
     */
    private double recencyDecayLambda = 0.995;
}
