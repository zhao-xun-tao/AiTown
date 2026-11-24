package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单回合思维链（CoT）完整记录，供科研观察面板实时滚动展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CotTurnLog {

    private String agentId;

    private String agentName;

    private long tick;

    private String timeDisplay;

    /** 环境感知输入摘要 */
    private String perceptionSummary;

    /** 检索到的记忆摘要列表 */
    @Builder.Default
    private List<String> retrievedMemories = new ArrayList<>();

    /** 内心独白 thought */
    private String thought;

    /** 最终动作 */
    private String action;

    private String targetLocation;

    private String targetAgent;

    private String speech;

    private boolean fallback;

    private String error;

    /** 接口耗时 ms */
    private Long latencyMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private String model;

    private long createdAtEpochMs;
}
