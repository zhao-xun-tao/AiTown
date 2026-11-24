package com.aitown.engine;

import com.aitown.memory.MemoryOrchestrator.MemoryBundle;
import com.aitown.model.Agent;
import com.aitown.model.AreaSpeechEvent;
import com.aitown.model.NearbyAgentInfo;
import com.aitown.model.PerceptionContext;
import com.aitown.model.ScoredMemory;
import com.aitown.model.WorkingMemoryItem;
import com.aitown.model.WorldEvent;
import org.springframework.stereotype.Component;

/**
 * 将人设、感知与双层记忆组装为结构化 Prompt。
 */
@Component
public class PromptBuilder {

    public String buildSystemPrompt(Agent agent) {
        return """
                你是小镇居民「%s」（职业：%s）。
                人设：%s

                你生活在一个 20x20 网格的 AI 小镇里。每个时间片你必须根据当前感知与记忆做出决策。
                你只能输出一个 JSON 对象，不要 Markdown，不要解释，不要代码块。

                JSON 格式严格如下：
                {
                  "thought": "内心独白与规划（中文）",
                  "action": "MOVE | TALK | IDLE",
                  "targetLocation": "目标地点名称或 null",
                  "targetAgent": "对话对象名字或 null",
                  "speech": "要说的话或 null",
                  "memoryNote": "本回合写入长期记忆的一句话摘要",
                  "memoryImportance": 1,
                  "memoryType": "OBSERVATION 或 DIALOGUE",
                  "impressionUpdates": [{"target":"对方名字","delta":1,"note":"印象变化说明"}]
                }

                规则：
                1. action=MOVE 时必须填 targetLocation（从已知地点中选），speech 可为 null。
                2. action=TALK 时必须填 speech；若有明确对象则填 targetAgent；memoryType 用 DIALOGUE。
                3. action=IDLE 时 targetLocation/targetAgent/speech 均可为 null；memoryType 用 OBSERVATION。
                4. 若附近有人说话，优先考虑回应（TALK），并结合记忆保持人物关系连贯。
                5. 若存在「世界事件」，必须在 thought 中体现你是否知晓/如何反应。
                6. 不要瞬移；MOVE 只表示朝该地点走去的意图。
                7. memoryImportance 为 1-10：日常琐事 1-3，社交互动 4-6，重要决定/情绪转折 7-10。
                8. memoryNote 用第一人称简述「此刻值得记住的事」。
                9. impressionUpdates 可选；delta 建议 -5~+5；无变化可给空数组。
                """.formatted(agent.getName(), agent.getRole(), agent.getPersona());
    }

    public String buildUserPrompt(Agent agent, PerceptionContext ctx, MemoryBundle memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 当前世界\n");
        sb.append("- 时间：").append(ctx.getTimeDisplay()).append("（Tick #").append(ctx.getTick()).append("）\n");
        sb.append("- 你的坐标：(").append(agent.getX()).append(", ").append(agent.getY()).append(")\n");
        sb.append("- 所在区域：").append(ctx.getCurrentAreaName())
                .append("（").append(ctx.getCurrentAreaType()).append("）\n");
        sb.append("- 你上一刻状态：").append(agent.getState())
                .append(" / 气泡：").append(nullToDash(agent.getBubble())).append('\n');

        sb.append("\n## 短期工作记忆（最近感知与对话）\n");
        if (memories == null || memories.workingMemories() == null || memories.workingMemories().isEmpty()) {
            sb.append("（暂无）\n");
        } else {
            for (WorkingMemoryItem item : memories.workingMemories()) {
                sb.append("- [").append(item.getTimeDisplay()).append("][")
                        .append(item.getType()).append("] ")
                        .append(item.getContent()).append('\n');
            }
        }

        sb.append("\n## 检索到的长期记忆 Top-K（时间衰减+重要性+相关性）\n");
        if (memories == null || memories.retrievedMemories() == null || memories.retrievedMemories().isEmpty()) {
            sb.append("（暂无）\n");
        } else {
            for (ScoredMemory sm : memories.retrievedMemories()) {
                var m = sm.getMemory();
                sb.append(String.format(
                        "- (score=%.3f rec=%.2f imp=%.2f rel=%.2f) [%s][重要性%d] %s%n",
                        sm.getScore(), sm.getRecencyScore(), sm.getImportanceScore(), sm.getRelevanceScore(),
                        m.getMemoryType(), m.getImportance(), m.getContent()));
            }
        }

        sb.append("\n## 世界事件（上帝视角投放，全镇可知）\n");
        if (ctx.getWorldEvents() == null || ctx.getWorldEvents().isEmpty()) {
            sb.append("（当前无特殊世界事件）\n");
        } else {
            for (WorldEvent ev : ctx.getWorldEvents()) {
                sb.append("- [剩余").append(ev.getRemainingTicks()).append("回合] ")
                        .append(ev.getContent()).append('\n');
            }
        }

        sb.append("\n## 附近居民\n");
        if (ctx.getNearbyAgents() == null || ctx.getNearbyAgents().isEmpty()) {
            sb.append("（附近没有其他人）\n");
        } else {
            for (NearbyAgentInfo n : ctx.getNearbyAgents()) {
                sb.append("- ").append(n.getName()).append("（").append(n.getRole()).append("）")
                        .append(" 距离=").append(n.getDistance())
                        .append(" 坐标=(").append(n.getX()).append(',').append(n.getY()).append(')')
                        .append(" 状态=").append(n.getState())
                        .append(" 气泡=").append(nullToDash(n.getBubble()))
                        .append('\n');
            }
        }

        sb.append("\n## 附近刚发生的对话（上一时刻）\n");
        if (ctx.getNearbySpeeches() == null || ctx.getNearbySpeeches().isEmpty()) {
            sb.append("（没有听到对话）\n");
        } else {
            for (AreaSpeechEvent ev : ctx.getNearbySpeeches()) {
                sb.append("- [").append(ev.getAreaName()).append("] ")
                        .append(ev.getSpeakerName());
                if (ev.getTargetAgentName() != null && !ev.getTargetAgentName().isBlank()) {
                    sb.append(" → ").append(ev.getTargetAgentName());
                }
                sb.append("：「").append(ev.getSpeech()).append("」\n");
            }
        }

        sb.append("\n## 已知地点（MOVE 时从此选择）\n");
        sb.append(String.join("、", ctx.getKnownLocations())).append('\n');

        sb.append("\n请输出你的决策 JSON。");
        return sb.toString();
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
