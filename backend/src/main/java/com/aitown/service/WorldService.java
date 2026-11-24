package com.aitown.service;

import com.aitown.config.AiTownProperties;
import com.aitown.engine.AgentDecisionService;
import com.aitown.engine.AreaEventBus;
import com.aitown.model.Agent;
import com.aitown.model.Building;
import com.aitown.model.GameTime;
import com.aitown.model.SimStatus;
import com.aitown.model.WorldState;
import com.aitown.websocket.WorldWebSocketHandler;
import com.aitown.world.AgentFactory;
import com.aitown.world.TownMapFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 世界状态中枢：持有地图、居民、时钟，并在 Tick 时驱动 Agent 决策与广播。
 * <p>
 * Phase 1 使用进程内内存；后续可将快照写入 Redis，档案/记忆流写入 MySQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorldService {

    private final AiTownProperties properties;
    private final AgentDecisionService agentDecisionService;
    private final AreaEventBus areaEventBus;
    private final WorldWebSocketHandler webSocketHandler;

    private final List<Building> buildings = new ArrayList<>();
    private final List<Agent> agents = new ArrayList<>();
    private GameTime gameTime;
    private volatile SimStatus simStatus = SimStatus.PAUSED;

    @PostConstruct
    public void init() {
        buildings.clear();
        buildings.addAll(TownMapFactory.createBuildings());

        agents.clear();
        agents.addAll(AgentFactory.createInitialAgents());

        areaEventBus.clear();

        gameTime = GameTime.builder()
                .day(1)
                .hour(properties.getStartHour())
                .minute(properties.getStartMinute())
                .tick(0)
                .build();

        log.info("小镇世界已初始化：地图 {}x{}，建筑 {} 处，居民 {} 人，起始时间 {}",
                TownMapFactory.MAP_SIZE, TownMapFactory.MAP_SIZE,
                buildings.size(), agents.size(), gameTime.format());
    }

    public synchronized WorldState snapshot() {
        return WorldState.builder()
                .mapSize(TownMapFactory.MAP_SIZE)
                .buildings(Collections.unmodifiableList(new ArrayList<>(buildings)))
                .agents(Collections.unmodifiableList(copyAgents()))
                .recentSpeeches(Collections.unmodifiableList(new ArrayList<>(areaEventBus.getPerceptibleEvents())))
                .gameTime(copyTime())
                .simStatus(simStatus)
                .timeDisplay(gameTime.format())
                .build();
    }

    public SimStatus getSimStatus() {
        return simStatus;
    }

    public synchronized void setSimStatus(SimStatus status) {
        this.simStatus = status;
        log.info("模拟状态切换为: {}", status);
        broadcastSnapshot();
    }

    /**
     * 执行一次世界 Tick：推进时钟 → 各 Agent 感知/决策/执行 → 固化区域对话 → 广播。
     */
    public synchronized WorldState tick() {
        gameTime.advanceMinutes(properties.getMinutesPerTick());

        // 依次决策：同 Tick 内互不影响感知（对话要等下一 Tick 才可听）
        for (Agent agent : agents) {
            try {
                agentDecisionService.decideAndAct(agent, agents, buildings, gameTime);
            } catch (Exception e) {
                // 单 Agent 异常绝不能打断整个模拟循环
                log.error("Agent {} Tick 执行异常，已跳过", agent.getName(), e);
                agent.setLastError(e.getMessage());
                agent.setLastAction("IDLE");
            }
        }

        // 将本 Tick 产生的对话推进为「下一 Tick 可感知」
        areaEventBus.advanceTick();

        WorldState state = snapshot();
        webSocketHandler.broadcast(state);
        log.info("Tick #{} 完成，时间={}，可感知对话数={}",
                gameTime.getTick(), gameTime.format(), areaEventBus.getPerceptibleEvents().size());
        return state;
    }

    public void broadcastSnapshot() {
        webSocketHandler.broadcast(snapshot());
    }

    private List<Agent> copyAgents() {
        List<Agent> copy = new ArrayList<>(agents.size());
        for (Agent a : agents) {
            copy.add(Agent.builder()
                    .id(a.getId())
                    .name(a.getName())
                    .role(a.getRole())
                    .persona(a.getPersona())
                    .x(a.getX())
                    .y(a.getY())
                    .state(a.getState())
                    .bubble(a.getBubble())
                    .lastThought(a.getLastThought())
                    .lastAction(a.getLastAction())
                    .lastSpeech(a.getLastSpeech())
                    .lastError(a.getLastError())
                    .color(a.getColor())
                    .build());
        }
        return copy;
    }

    private GameTime copyTime() {
        return GameTime.builder()
                .day(gameTime.getDay())
                .hour(gameTime.getHour())
                .minute(gameTime.getMinute())
                .tick(gameTime.getTick())
                .build();
    }
}
