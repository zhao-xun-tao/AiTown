package com.aitown.service;

import com.aitown.config.AiTownProperties;
import com.aitown.engine.AgentDecisionService;
import com.aitown.engine.AreaEventBus;
import com.aitown.model.Agent;
import com.aitown.model.Building;
import com.aitown.model.GameTime;
import com.aitown.model.GridPoint;
import com.aitown.model.SimStatus;
import com.aitown.model.WorldEvent;
import com.aitown.model.WorldState;
import com.aitown.observability.RelationshipService;
import com.aitown.observability.WorldEventService;
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
 * 世界状态中枢：地图、居民、时钟、上帝事件，并驱动决策与广播。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorldService {

    private final AiTownProperties properties;
    private final AgentDecisionService agentDecisionService;
    private final AreaEventBus areaEventBus;
    private final WorldEventService worldEventService;
    private final RelationshipService relationshipService;
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
        relationshipService.ensureInitialized(agents);

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
                .activeWorldEvents(Collections.unmodifiableList(new ArrayList<>(worldEventService.getActiveEvents())))
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

    public synchronized GameTime currentTime() {
        return copyTime();
    }

    public List<Agent> getAgents() {
        return Collections.unmodifiableList(agents);
    }

    /** 上帝视角投放世界事件，并立即广播 */
    public synchronized WorldEvent injectWorldEvent(String content, Integer durationTicks) {
        int dur = durationTicks == null ? WorldEventService.DEFAULT_DURATION_TICKS : durationTicks;
        WorldEvent event = worldEventService.inject(content, gameTime, dur);
        broadcastSnapshot();
        return event;
    }

    public synchronized WorldState tick() {
        gameTime.advanceMinutes(properties.getMinutesPerTick());

        for (Agent agent : agents) {
            try {
                agentDecisionService.decideAndAct(agent, agents, buildings, gameTime);
            } catch (Exception e) {
                log.error("Agent {} Tick 执行异常，已跳过", agent.getName(), e);
                agent.setLastError(e.getMessage());
                agent.setLastAction("IDLE");
            }
        }

        areaEventBus.advanceTick();
        worldEventService.onTickEnd();

        WorldState state = snapshot();
        webSocketHandler.broadcast(state);
        log.info("Tick #{} 完成，时间={}，活跃世界事件={}",
                gameTime.getTick(), gameTime.format(), worldEventService.getActiveEvents().size());
        return state;
    }

    public void broadcastSnapshot() {
        webSocketHandler.broadcast(snapshot());
    }

    private List<Agent> copyAgents() {
        List<Agent> copy = new ArrayList<>(agents.size());
        for (Agent a : agents) {
            List<GridPoint> pathCopy = new ArrayList<>();
            if (a.getPath() != null) {
                for (GridPoint p : a.getPath()) {
                    pathCopy.add(GridPoint.builder().x(p.getX()).y(p.getY()).build());
                }
            }
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
                    .retrievedMemorySummary(a.getRetrievedMemorySummary())
                    .color(a.getColor())
                    .targetX(a.getTargetX())
                    .targetY(a.getTargetY())
                    .path(pathCopy)
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
