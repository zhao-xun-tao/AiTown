package com.aitown.model;

/**
 * 长期记忆类型（对齐 Generative Agents memory stream）。
 */
public enum MemoryType {
    OBSERVATION,
    DIALOGUE,
    REFLECTION;

    public static MemoryType parseSafe(String raw, MemoryType defaultType) {
        if (raw == null || raw.isBlank()) {
            return defaultType;
        }
        try {
            return MemoryType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultType;
        }
    }
}
