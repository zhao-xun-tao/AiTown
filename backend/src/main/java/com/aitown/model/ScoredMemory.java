package com.aitown.model;

import com.aitown.persistence.entity.AgentMemoryEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 带检索综合分的长期记忆（注入 Prompt / 观察面板）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoredMemory {

    private AgentMemoryEntity memory;

    private double score;

    private double recencyScore;

    private double importanceScore;

    private double relevanceScore;
}
