package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 Agent 在本 Tick 的环境感知快照，用于组装 Prompt。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerceptionContext {

    private String timeDisplay;

    private long tick;

    /** 当前所在区域名称 */
    private String currentAreaName;

    private String currentAreaType;

    private int x;

    private int y;

    @Builder.Default
    private List<NearbyAgentInfo> nearbyAgents = new ArrayList<>();

    /** 附近/同区域刚发生的对话（来自上一 Tick） */
    @Builder.Default
    private List<AreaSpeechEvent> nearbySpeeches = new ArrayList<>();

    /** 可选目标地点清单，约束 LLM 的 targetLocation */
    @Builder.Default
    private List<String> knownLocations = new ArrayList<>();
}
