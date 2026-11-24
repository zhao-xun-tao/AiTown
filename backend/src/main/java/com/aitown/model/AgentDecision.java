package com.aitown.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 强制返回的结构化决策 JSON。
 * <pre>
 * {
 *   "thought": "...",
 *   "action": "MOVE | TALK | IDLE",
 *   "targetLocation": "咖啡馆/公园等或null",
 *   "targetAgent": "对方名字或null",
 *   "speech": "说出的话或null"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentDecision {

    private String thought;

    private String action;

    private String targetLocation;

    private String targetAgent;

    private String speech;

    /**
     * 本回合要写入长期记忆的一句话（LLM 生成记忆时顺便产出）。
     */
    private String memoryNote;

    /** 该记忆重要性 1-10 */
    private Integer memoryImportance;

    /** OBSERVATION / DIALOGUE（反思由系统触发，不由此字段指定） */
    private String memoryType;

    /** 对其他居民的印象/好感度更新（可选） */
    private java.util.List<ImpressionUpdate> impressionUpdates;

    /** 解析失败 / 超时等标记，供日志与观察面板使用 */
    @Builder.Default
    private boolean fallback = false;

    private String fallbackReason;

    public AgentActionType actionType() {
        return AgentActionType.parseSafe(action);
    }

    /** 超时或 JSON 损坏时的兜底 IDLE */
    public static AgentDecision idleFallback(String reason) {
        return AgentDecision.builder()
                .thought("[系统] 决策失败，进入 IDLE：" + reason)
                .action("IDLE")
                .targetLocation(null)
                .targetAgent(null)
                .speech(null)
                .fallback(true)
                .fallbackReason(reason)
                .build();
    }
}
