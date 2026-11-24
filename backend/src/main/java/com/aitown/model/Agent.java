package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 居民（Agent）运行时档案。
 * Phase 1 仅内存持有；后续会持久化到 MySQL，并把坐标/短期记忆放到 Redis。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent {

    private String id;

    /** 显示名，如「林悦」 */
    private String name;

    /** 角色标签，如「咖啡师」 */
    private String role;

    /** 人设描述，供后续 LLM Prompt 使用 */
    private String persona;

    /** 当前网格坐标 X */
    private int x;

    /** 当前网格坐标 Y */
    private int y;

    /** 当前活动状态 */
    private AgentState state;

    /** 头顶气泡文案（对话 / 状态摘要） */
    private String bubble;

    /** 最近一次思考 / CoT（LLM thought 或 Mock 摘要） */
    private String lastThought;

    /** 最近一次结构化动作：MOVE / TALK / IDLE */
    private String lastAction;

    /** 最近一次说出的话（若有） */
    private String lastSpeech;

    /** 最近一次决策错误（超时 / JSON 损坏等），正常时为 null */
    private String lastError;

    /** 本 Tick 检索到的长期记忆摘要（研究可观测性） */
    private String retrievedMemorySummary;

    /** 渲染用颜色（前端 Canvas） */
    private String color;

    /** 当前寻路最终目标格 X（无移动时可为当前位置） */
    private Integer targetX;

    /** 当前寻路最终目标格 Y */
    private Integer targetY;

    /**
     * 剩余路径路点（不含当前格）。后端 A* 生成；前端插值与局部重寻路。
     */
    @Builder.Default
    private List<GridPoint> path = new ArrayList<>();
}
