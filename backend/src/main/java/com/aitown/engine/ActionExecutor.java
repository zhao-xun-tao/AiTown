package com.aitown.engine;

import com.aitown.model.Agent;
import com.aitown.model.AgentDecision;
import com.aitown.model.AgentState;
import com.aitown.model.AreaSpeechEvent;
import com.aitown.model.Building;
import com.aitown.model.GameTime;
import com.aitown.model.GridPoint;
import com.aitown.world.TownMapFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 将结构化决策落实为世界状态变更：MOVE / TALK / IDLE。
 * MOVE 使用 A* 生成路径，每 Tick 沿路径走一格。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionExecutor {

    private final PerceptionService perceptionService;
    private final AreaEventBus areaEventBus;
    private final PathfindingService pathfindingService;

    public void execute(Agent agent, AgentDecision decision, List<Building> buildings, GameTime time) {
        agent.setLastThought(decision.getThought());
        agent.setLastAction(decision.actionType().name());
        agent.setLastSpeech(decision.getSpeech());
        agent.setLastError(decision.isFallback() ? decision.getFallbackReason() : null);

        switch (decision.actionType()) {
            case MOVE -> doMove(agent, decision, buildings);
            case TALK -> doTalk(agent, decision, buildings, time);
            default -> doIdle(agent, buildings);
        }

        agent.setX(clamp(agent.getX(), 0, TownMapFactory.MAP_SIZE - 1));
        agent.setY(clamp(agent.getY(), 0, TownMapFactory.MAP_SIZE - 1));
    }

    /**
     * 朝目标建筑门廊寻路：若已有指向同一目标的 path 则续走，否则重算 A*。
     */
    private void doMove(Agent agent, AgentDecision decision, List<Building> buildings) {
        Optional<Building> target = resolveLocation(decision.getTargetLocation(), buildings);
        if (target.isEmpty()) {
            log.info("Agent {} MOVE 目标无法解析「{}」，降级 IDLE", agent.getName(), decision.getTargetLocation());
            agent.setLastAction("IDLE");
            doIdle(agent, buildings);
            return;
        }

        Building b = target.get();
        CollisionMap map = CollisionMap.fromBuildings(buildings);
        int rawTx = b.getX() + b.getWidth() / 2;
        int rawTy = b.getY() + b.getHeight() / 2;
        int[] goal = map.snapToWalkable(rawTx, rawTy, buildings);
        int tx = goal[0];
        int ty = goal[1];

        agent.setTargetX(tx);
        agent.setTargetY(ty);

        // 已在目标区域门廊：停下
        if (agent.getX() == tx && agent.getY() == ty) {
            clearPath(agent);
            agent.setState(AgentState.IDLE);
            agent.setBubble("已到达「" + b.getName() + "」");
            return;
        }
        if (b.contains(agent.getX(), agent.getY()) && map.isWalkable(agent.getX(), agent.getY())) {
            // 已进入目标门廊带
            if (Math.abs(agent.getX() - tx) + Math.abs(agent.getY() - ty) <= 1) {
                clearPath(agent);
                agent.setState(AgentState.IDLE);
                agent.setBubble("已到达「" + b.getName() + "」");
                return;
            }
        }

        List<GridPoint> path = agent.getPath();
        boolean needReplan = path == null || path.isEmpty()
                || agent.getTargetX() == null
                || agent.getTargetY() == null
                || agent.getTargetX() != tx
                || agent.getTargetY() != ty
                || !pathLeadsNear(path, tx, ty);

        if (needReplan) {
            path = pathfindingService.findPath(agent.getX(), agent.getY(), tx, ty, buildings);
            agent.setPath(new ArrayList<>(path));
        }

        if (path == null || path.isEmpty()) {
            // A* 失败：安全降级，原地
            log.warn("Agent {} 寻路失败 {} -> ({},{})，IDLE", agent.getName(), agent.getName(), tx, ty);
            clearPath(agent);
            agent.setState(AgentState.IDLE);
            agent.setBubble("无法到达「" + b.getName() + "」");
            return;
        }

        // 沿路径走一格
        GridPoint next = path.remove(0);
        agent.setPath(new ArrayList<>(path));
        if (map.isWalkable(next.getX(), next.getY())) {
            agent.setX(next.getX());
            agent.setY(next.getY());
        } else {
            // 下一格突然不可走：清空让下 Tick 重寻路
            clearPath(agent);
        }

        agent.setState(AgentState.WALKING);
        agent.setBubble("前往「" + b.getName() + "」…");
    }

    private boolean pathLeadsNear(List<GridPoint> path, int tx, int ty) {
        if (path.isEmpty()) {
            return false;
        }
        GridPoint last = path.get(path.size() - 1);
        return Math.abs(last.getX() - tx) + Math.abs(last.getY() - ty) <= 1;
    }

    private void clearPath(Agent agent) {
        agent.setPath(new ArrayList<>());
    }

    private void doTalk(Agent agent, AgentDecision decision, List<Building> buildings, GameTime time) {
        String speech = decision.getSpeech();
        if (speech == null || speech.isBlank()) {
            log.info("Agent {} TALK 但 speech 为空，降级 IDLE", agent.getName());
            doIdle(agent, buildings);
            return;
        }

        clearPath(agent);
        agent.setTargetX(agent.getX());
        agent.setTargetY(agent.getY());
        agent.setState(AgentState.CHATTING);
        String bubble = speech.length() > 24 ? speech.substring(0, 24) + "…" : speech;
        if (decision.getTargetAgent() != null) {
            agent.setBubble("对" + decision.getTargetAgent() + "：" + bubble);
        } else {
            agent.setBubble(bubble);
        }

        areaEventBus.publish(AreaSpeechEvent.builder()
                .tick(time.getTick())
                .areaKey(perceptionService.areaKeyAt(agent.getX(), agent.getY(), buildings))
                .areaName(perceptionService.areaNameAt(agent.getX(), agent.getY(), buildings))
                .speakerId(agent.getId())
                .speakerName(agent.getName())
                .targetAgentName(decision.getTargetAgent())
                .speech(speech)
                .x(agent.getX())
                .y(agent.getY())
                .build());
    }

    private void doIdle(Agent agent, List<Building> buildings) {
        clearPath(agent);
        agent.setTargetX(agent.getX());
        agent.setTargetY(agent.getY());
        Building here = perceptionService.findBuilding(agent.getX(), agent.getY(), buildings);
        // 在功能区时映射更具体的状态，供前端 Think 动画
        if (here != null) {
            agent.setState(switch (here.getType()) {
                case CAFE -> AgentState.WORKING;
                case LIBRARY -> AgentState.READING;
                case PARK -> AgentState.RESTING;
                case HOUSE -> AgentState.WRITING;
                default -> AgentState.IDLE;
            });
            agent.setBubble("在「" + here.getName() + "」停留");
        } else {
            agent.setState(AgentState.IDLE);
            agent.setBubble("原地发呆…");
        }
    }

    Optional<Building> resolveLocation(String raw, List<Building> buildings) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String key = raw.trim();

        for (Building b : buildings) {
            if (b.getName().equals(key)) {
                return Optional.of(b);
            }
        }
        for (Building b : buildings) {
            if (b.getName().contains(key) || key.contains(b.getName())) {
                return Optional.of(b);
            }
        }

        String lower = key.toLowerCase(Locale.ROOT);
        for (Building b : buildings) {
            String alias = switch (b.getType()) {
                case CAFE -> "咖啡馆";
                case LIBRARY -> "图书馆";
                case PARK -> "公园";
                case PLAZA -> "广场";
                case HOUSE -> "住宅";
                default -> "";
            };
            if (!alias.isEmpty() && (key.contains(alias) || lower.contains(alias))) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
