package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 推送给前端的完整世界快照（REST 查询与 WebSocket 广播共用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorldState {

    /** 地图边长（默认 20） */
    private int mapSize;

    /** 功能建筑列表 */
    private List<Building> buildings;

    /** 所有居民 */
    private List<Agent> agents;

    /** 上一 Tick 固化、当前可感知的区域对话（研究可观测） */
    private List<AreaSpeechEvent> recentSpeeches;

    /** 当前生效的上帝视角世界事件 */
    private List<WorldEvent> activeWorldEvents;

    /** 游戏内时间 */
    private GameTime gameTime;

    /** 模拟引擎状态 */
    private SimStatus simStatus;

    /** 时间显示文案，方便前端直接展示 */
    private String timeDisplay;
}
