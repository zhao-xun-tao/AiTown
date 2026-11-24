package com.aitown.engine;

import com.aitown.model.Agent;
import com.aitown.model.AgentState;
import com.aitown.model.Building;
import com.aitown.model.GameTime;
import com.aitown.world.TownMapFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock Agent 决策服务（Phase 1 占位，不调用真实 LLM）。
 * <p>
 * 后续真实链路：检索记忆 → 组装 Prompt → 调用 OpenAI 兼容 API →
 * 强制解析 JSON（thought / action / target / speech）→ 容错兜底。
 * 本类模拟「走走停停 + 切换状态 + 更新气泡」，保证模拟循环可跑通。
 */
@Service
public class MockAgentDecisionService {

    /**
     * 对单个 Agent 执行一次 Mock 决策与位移。
     */
    public void decideAndAct(Agent agent, List<Building> buildings, GameTime time) {
        Random rng = ThreadLocalRandom.current();

        // 约 60% 概率尝试向兴趣点迈一步，否则原地活动
        if (rng.nextDouble() < 0.6) {
            Building target = pickPreferredBuilding(agent, buildings, rng);
            stepToward(agent, target, rng);
            agent.setState(AgentState.WALKING);
            agent.setBubble("前往「" + target.getName() + "」…");
            agent.setLastAction("MOVE");
            agent.setLastSpeech(null);
            agent.setLastError(null);
            agent.setLastThought(String.format(
                    "[Mock] 当前 %s，我想去 %s 看看。",
                    time.format(), target.getName()));
        } else {
            applyIdleActivity(agent, buildings, time, rng);
        }

        // 钳制坐标在地图范围内
        agent.setX(clamp(agent.getX(), 0, TownMapFactory.MAP_SIZE - 1));
        agent.setY(clamp(agent.getY(), 0, TownMapFactory.MAP_SIZE - 1));
    }

    /** 按角色偏好随机选一个目标建筑 */
    private Building pickPreferredBuilding(Agent agent, List<Building> buildings, Random rng) {
        List<Building> preferred = buildings.stream()
                .filter(b -> matchesPreference(agent.getRole(), b))
                .toList();
        List<Building> pool = preferred.isEmpty() ? buildings : preferred;
        return pool.get(rng.nextInt(pool.size()));
    }

    private boolean matchesPreference(String role, Building b) {
        return switch (role) {
            case "咖啡师" -> b.getType().name().equals("CAFE")
                    || b.getType().name().equals("PLAZA")
                    || b.getType().name().equals("PARK");
            case "程序员" -> b.getType().name().equals("LIBRARY")
                    || b.getType().name().equals("CAFE")
                    || b.getType().name().equals("HOUSE");
            case "作家" -> b.getType().name().equals("PARK")
                    || b.getType().name().equals("CAFE")
                    || b.getType().name().equals("LIBRARY");
            default -> true;
        };
    }

    /** 朝目标建筑中心走一格（含随机抖动，避免完全直线） */
    private void stepToward(Agent agent, Building target, Random rng) {
        int tx = target.getX() + target.getWidth() / 2;
        int ty = target.getY() + target.getHeight() / 2;

        int dx = Integer.compare(tx, agent.getX());
        int dy = Integer.compare(ty, agent.getY());

        // 随机优先横走或纵走
        if (rng.nextBoolean() && dx != 0) {
            agent.setX(agent.getX() + dx);
        } else if (dy != 0) {
            agent.setY(agent.getY() + dy);
        } else if (dx != 0) {
            agent.setX(agent.getX() + dx);
        }
    }

    /** 原地活动：根据当前所在建筑切换状态与气泡 */
    private void applyIdleActivity(Agent agent, List<Building> buildings, GameTime time, Random rng) {
        Building here = buildings.stream()
                .filter(b -> b.contains(agent.getX(), agent.getY()))
                .findFirst()
                .orElse(null);

        agent.setLastAction("IDLE");
        agent.setLastSpeech(null);
        agent.setLastError(null);

        if (here == null) {
            agent.setState(AgentState.IDLE);
            agent.setBubble("在路上发呆…");
            agent.setLastThought("[Mock] 暂时没什么特别想做的。");
            return;
        }

        switch (here.getType()) {
            case CAFE -> {
                if ("咖啡师".equals(agent.getRole())) {
                    agent.setState(AgentState.WORKING);
                    agent.setBubble(pick(rng, "正在调制美式…", "擦拭吧台…", "和客人寒暄…"));
                } else {
                    agent.setState(AgentState.CHATTING);
                    agent.setBubble(pick(rng, "点了一杯拿铁", "和林悦聊天…", "边喝边看窗外"));
                }
            }
            case LIBRARY -> {
                agent.setState(AgentState.READING);
                agent.setBubble(pick(rng, "翻开技术书…", "在安静角落写代码", "查阅资料中"));
            }
            case PARK -> {
                agent.setState(AgentState.RESTING);
                agent.setBubble(pick(rng, "坐在长椅上发呆", "观察行人…", "呼吸新鲜空气"));
            }
            case PLAZA -> {
                agent.setState(AgentState.CHATTING);
                agent.setBubble(pick(rng, "在广场闲逛", "看喷泉发呆", "和其他人打招呼"));
            }
            case HOUSE -> {
                agent.setState(AgentState.RESTING);
                agent.setBubble(pick(rng, "在家休息…", "整理书桌", "小憩片刻"));
            }
            default -> {
                agent.setState(AgentState.IDLE);
                agent.setBubble("四处看看…");
            }
        }

        if ("作家".equals(agent.getRole()) && rng.nextBoolean()) {
            agent.setState(AgentState.WRITING);
            agent.setBubble(pick(rng, "记下一段对话", "修改小说章节…", "捕捉灵感"));
        }

        agent.setLastThought(String.format(
                "[Mock] 我在「%s」，现在是 %s，先待一会儿。",
                here.getName(), time.format()));
    }

    private static String pick(Random rng, String... options) {
        return options[rng.nextInt(options.length)];
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
