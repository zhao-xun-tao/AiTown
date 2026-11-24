package com.aitown.engine;

import com.aitown.model.Agent;
import com.aitown.model.AreaSpeechEvent;
import com.aitown.model.NearbyAgentInfo;
import com.aitown.model.PerceptionContext;
import org.springframework.stereotype.Component;

/**
 * 将人设与感知上下文组装为结构化 Prompt。
 */
@Component
public class PromptBuilder {

    public String buildSystemPrompt(Agent agent) {
        return """
                你是小镇居民「%s」（职业：%s）。
                人设：%s

                你生活在一个 20x20 网格的 AI 小镇里。每个时间片你必须根据当前感知做出决策。
                你只能输出一个 JSON 对象，不要 Markdown，不要解释，不要代码块。

                JSON 格式严格如下：
                {
                  "thought": "内心独白与规划（中文）",
                  "action": "MOVE | TALK | IDLE",
                  "targetLocation": "目标地点名称或 null",
                  "targetAgent": "对话对象名字或 null",
                  "speech": "要说的话或 null"
                }

                规则：
                1. action=MOVE 时必须填 targetLocation（从已知地点中选），speech 可为 null。
                2. action=TALK 时必须填 speech；若有明确对象则填 targetAgent。
                3. action=IDLE 时 targetLocation/targetAgent/speech 均可为 null。
                4. 若附近有人说话，优先考虑回应（TALK），保持对话自然连贯。
                5. 不要瞬移；MOVE 只表示朝该地点走去的意图。
                """.formatted(agent.getName(), agent.getRole(), agent.getPersona());
    }

    public String buildUserPrompt(Agent agent, PerceptionContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 当前世界\n");
        sb.append("- 时间：").append(ctx.getTimeDisplay()).append("（Tick #").append(ctx.getTick()).append("）\n");
        sb.append("- 你的坐标：(").append(agent.getX()).append(", ").append(agent.getY()).append(")\n");
        sb.append("- 所在区域：").append(ctx.getCurrentAreaName())
                .append("（").append(ctx.getCurrentAreaType()).append("）\n");
        sb.append("- 你上一刻状态：").append(agent.getState())
                .append(" / 气泡：").append(nullToDash(agent.getBubble())).append('\n');

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
