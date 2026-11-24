package com.aitown.model;

/**
 * LLM 结构化输出中的动作类型。
 */
public enum AgentActionType {
    MOVE,
    TALK,
    IDLE;

    /**
     * 容错解析：未知值一律视为 IDLE，避免模拟崩溃。
     */
    public static AgentActionType parseSafe(String raw) {
        if (raw == null || raw.isBlank()) {
            return IDLE;
        }
        try {
            return AgentActionType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return IDLE;
        }
    }
}
