package com.aitown.engine;

import com.aitown.config.LlmProperties;
import com.aitown.llm.LlmClient;
import com.aitown.llm.LlmException;
import com.aitown.model.Agent;
import com.aitown.model.AgentDecision;
import com.aitown.model.Building;
import com.aitown.model.GameTime;
import com.aitown.model.PerceptionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 自主决策闭环：感知 → Prompt → LLM → 解析 → 执行。
 * <p>
 * 任何 LLM/解析失败均降级为 IDLE，保证模拟 Tick 不中断。
 * 当 llm.enabled=false 时，委托 {@link MockAgentDecisionService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDecisionService {

    private final LlmProperties llmProperties;
    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final PerceptionService perceptionService;
    private final AgentDecisionParser decisionParser;
    private final ActionExecutor actionExecutor;
    private final MockAgentDecisionService mockAgentDecisionService;

    /**
     * 对单个 Agent 完成本 Tick 的决策与动作执行。
     */
    public void decideAndAct(Agent agent, List<Agent> allAgents, List<Building> buildings, GameTime time) {
        if (!llmProperties.isEnabled()
                || llmProperties.getApiKey() == null
                || llmProperties.getApiKey().isBlank()) {
            if (llmProperties.isEnabled()) {
                log.warn("llm.enabled=true 但 api-key 为空，Agent {} 本 Tick 使用 Mock 决策", agent.getName());
            }
            mockAgentDecisionService.decideAndAct(agent, buildings, time);
            return;
        }

        PerceptionContext perception = perceptionService.build(agent, allAgents, buildings, time);
        AgentDecision decision = invokeLlm(agent, perception);
        actionExecutor.execute(agent, decision, buildings, time);

        log.info("Agent {} 决策: action={}, loc={}, target={}, fallback={}",
                agent.getName(),
                decision.getAction(),
                decision.getTargetLocation(),
                decision.getTargetAgent(),
                decision.isFallback());
    }

    private AgentDecision invokeLlm(Agent agent, PerceptionContext perception) {
        String system = promptBuilder.buildSystemPrompt(agent);
        String user = promptBuilder.buildUserPrompt(agent, perception);
        try {
            String raw = llmClient.chat(system, user);
            AgentDecision decision = decisionParser.parse(raw);
            if (decision.isFallback()) {
                log.error("Agent {} JSON 容错降级 IDLE: {}", agent.getName(), decision.getFallbackReason());
            }
            return decision;
        } catch (LlmException e) {
            log.error("Agent {} LLM 调用失败，降级 IDLE: {}", agent.getName(), e.getMessage());
            return AgentDecision.idleFallback(e.getMessage());
        } catch (Exception e) {
            log.error("Agent {} 决策链路异常，降级 IDLE", agent.getName(), e);
            return AgentDecision.idleFallback("未预期异常: " + e.getMessage());
        }
    }
}
