package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上帝视角投放的全局世界事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorldEvent {

    private String id;

    /** 事件正文 */
    private String content;

    /** 投放时的游戏 Tick */
    private long createdTick;

    private String createdTimeDisplay;

    /** 剩余可被感知的 Tick 数（含本 Tick） */
    private int remainingTicks;

    /** 初始持续 Tick 数 */
    private int durationTicks;
}
