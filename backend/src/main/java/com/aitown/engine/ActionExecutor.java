package com.aitown.engine;

import com.aitown.model.Agent;
import com.aitown.model.AgentActionType;
import com.aitown.model.AgentDecision;
import com.aitown.model.AgentState;
import com.aitown.model.AreaSpeechEvent;
import com.aitown.model.Building;
import com.aitown.model.GameTime;
import com.aitown.world.TownMapFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 将结构化决策落实为世界状态变更：MOVE / TALK / IDLE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionExecutor {

    private final PerceptionService perceptionService;
    private final AreaEventBus areaEventBus;

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

    /** 朝目标建筑中心走一格（每 Tick 一步，形成寻路效果） */
    private void doMove(Agent agent, AgentDecision decision, List<Building> buildings) {
        Optional<Building> target = resolveLocation(decision.getTargetLocation(), buildings);
        if (target.isEmpty()) {
            log.info("Agent {} MOVE 目标无法解析「{}」，降级 IDLE", agent.getName(), decision.getTargetLocation());
            agent.setLastAction("IDLE");
            doIdle(agent, buildings);
            return;
        }

        Building b = target.get();
        int tx = b.getX() + b.getWidth() / 2;
        int ty = b.getY() + b.getHeight() / 2;

        // 已到达目标区域：原地停留
        if (b.contains(agent.getX(), agent.getY())) {
            agent.setState(AgentState.IDLE);
            agent.setBubble("已到达「" + b.getName() + "」");
            return;
        }

        int dx = Integer.compare(tx, agent.getX());
        int dy = Integer.compare(ty, agent.getY());
        // 优先缩小距离更大的轴
        if (Math.abs(tx - agent.getX()) >= Math.abs(ty - agent.getY())) {
            if (dx != 0) {
                agent.setX(agent.getX() + dx);
            } else if (dy != 0) {
                agent.setY(agent.getY() + dy);
            }
        } else {
            if (dy != 0) {
                agent.setY(agent.getY() + dy);
            } else if (dx != 0) {
                agent.setX(agent.getX() + dx);
            }
        }

        agent.setState(AgentState.WALKING);
        agent.setBubble("前往「" + b.getName() + "」…");
    }

    /** 说话：更新气泡，并写入区域事件供下一 Tick 感知 */
    private void doTalk(Agent agent, AgentDecision decision, List<Building> buildings, GameTime time) {
        String speech = decision.getSpeech();
        if (speech == null || speech.isBlank()) {
            log.info("Agent {} TALK 但 speech 为空，降级 IDLE", agent.getName());
            doIdle(agent, buildings);
            return;
        }

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
        Building here = perceptionService.findBuilding(agent.getX(), agent.getY(), buildings);
        agent.setState(AgentState.IDLE);
        if (here != null) {
            agent.setBubble("在「" + here.getName() + "」停留");
        } else {
            agent.setBubble("原地发呆…");
        }
    }

    /**
     * 将 LLM 给出的地点文案解析为建筑：先精确匹配名称，再别名/包含匹配。
     */
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
