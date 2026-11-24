package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 区域内发生的对话事件，供下一 Tick 附近 Agent 感知。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AreaSpeechEvent {

    /** 发生时的游戏 Tick */
    private long tick;

    /** 区域键：建筑 id，或不在建筑内时为 road */
    private String areaKey;

    /** 区域可读名 */
    private String areaName;

    private String speakerId;

    private String speakerName;

    /** 说话对象（可空） */
    private String targetAgentName;

    private String speech;

    private int x;

    private int y;
}
