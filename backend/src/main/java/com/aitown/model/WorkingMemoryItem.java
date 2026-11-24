package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 短期工作记忆条目（每个 Agent 保留最近 N 条）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingMemoryItem {

    private long tick;

    private String timeDisplay;

    private String type;

    private String content;

    private long createdAtEpochMs;
}
