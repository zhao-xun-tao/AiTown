package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A 对 B 的社会印象与好感度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRelation {

    private String fromAgentId;

    private String fromAgentName;

    private String toAgentId;

    private String toAgentName;

    /** 好感度 0-100，初始 50 */
    @Builder.Default
    private int favorability = 50;

    /** 当前印象摘要 */
    private String impression;

    @Builder.Default
    private List<RelationChange> history = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationChange {
        private long tick;
        private String timeDisplay;
        private int delta;
        private int favorabilityAfter;
        private String note;
    }
}
