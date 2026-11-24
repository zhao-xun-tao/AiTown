package com.aitown.world;

import com.aitown.model.Agent;
import com.aitown.model.AgentState;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建 3 个初始 AI 居民及其人设、出生点。
 */
public final class AgentFactory {

    private AgentFactory() {
    }

    public static List<Agent> createInitialAgents() {
        List<Agent> agents = new ArrayList<>();

        // 咖啡师林悦：出生在咖啡馆附近
        agents.add(Agent.builder()
                .id("agent-linyue")
                .name("林悦")
                .role("咖啡师")
                .persona("温和细心的咖啡师，热爱与客人聊天，对小镇人情世故了如指掌。"
                        + "日常会在咖啡馆工作，偶尔去公园散步放松。")
                // 咖啡馆南侧门廊（可走格）
                .x(16).y(11)
                .state(AgentState.WORKING)
                .bubble("正在调制拿铁…")
                .lastThought("早上客流还不多，先把机器预热好。")
                .color("#E07A5F")
                .build());

        // 程序员陈默：出生在东北住宅门廊
        agents.add(Agent.builder()
                .id("agent-chenmo")
                .name("陈默")
                .role("程序员")
                .persona("内向专注的程序员，白天喜欢去图书馆写代码，偶尔到咖啡馆换换环境。"
                        + "说话简洁，但对技术话题很有热情。")
                .x(16).y(4)
                .state(AgentState.IDLE)
                .bubble("准备出门…")
                .lastThought("今天想去图书馆把那个重构做完。")
                .color("#3D5A80")
                .build());

        // 作家苏晴：出生在西北住宅门廊
        agents.add(Agent.builder()
                .id("agent-suqing")
                .name("苏晴")
                .role("作家")
                .persona("感性细腻的作家，常在公园观察行人寻找灵感，也会泡在咖啡馆写作。"
                        + "喜欢和林悦闲聊，收集故事素材。")
                .x(2).y(4)
                .state(AgentState.WRITING)
                .bubble("在记灵感…")
                .lastThought("晨光不错，先记下昨晚梦里的片段。")
                .color("#81B29A")
                .build());

        return agents;
    }
}
