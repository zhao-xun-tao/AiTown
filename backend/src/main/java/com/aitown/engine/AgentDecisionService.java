package com.aitown.engine;

import com.aitown.config.LlmProperties;
import com.aitown.llm.LlmCallResult;
import com.aitown.llm.LlmClient;
import com.aitown.llm.LlmException;
import com.aitown.memory.MemoryOrchestrator;
import com.aitown.memory.MemoryOrchestrator.MemoryBundle;
import com.aitown.model.Agent;
import com.aitown.model.AgentDecision;
import com.aitown.model.AreaSpeechEvent;
import com.aitown.model.Building;
import com.aitown.model.CotTurnLog;
import com.aitown.model.GameTime;
import com.aitown.model.NearbyAgentInfo;
import com.aitown.model.PerceptionContext;
import com.aitown.model.ScoredMemory;
import com.aitown.model.WorldEvent;
import com.aitown.observability.CotLogService;
import com.aitown.observability.RelationshipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 自主决策闭环：感知 → 记忆检索 → Prompt → LLM → 解析 → 执行 → 记忆/关系/CoT。
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
    private final MemoryOrchestrator memoryOrchestrator;
    private final CotLogService cotLogService;
    private final RelationshipService relationshipService;

    public void decideAndAct(Agent agent, List<Agent> allAgents, List<Building> buildings, GameTime time) {
        if (!llmProperties.isEnabled()
                || llmProperties.getApiKey() == null
                || llmProperties.getApiKey().isBlank()) {
            if (llmProperties.isEnabled()) {
                log.warn("llm.enabled=true 但 api-key 为空，Agent {} 本 Tick 使用 Mock 决策", agent.getName());
            }
            mockAgentDecisionService.decideAndAct(agent, buildings, time);
            recordMockCot(agent, time);
            return;
        }

        PerceptionContext perception = perceptionService.build(agent, allAgents, buildings, time);
        MemoryBundle memories = memoryOrchestrator.prepareForDecision(agent, perception, time);
        agent.setRetrievedMemorySummary(summarizeRetrieved(memories));

        String system = promptBuilder.buildSystemPrompt(agent);
        String user = promptBuilder.buildUserPrompt(agent, perception, memories);

        LlmCallResult llmResult = null;
        AgentDecision decision;
        try {
            llmResult = llmClient.chatDetailed(system, user);
            decision = decisionParser.parse(llmResult.getContent());
            if (decision.isFallback()) {
                log.error("Agent {} JSON 容错降级 IDLE: {}", agent.getName(), decision.getFallbackReason());
            }
        } catch (LlmException e) {
            log.error("Agent {} LLM 调用失败，降级 IDLE: {}", agent.getName(), e.getMessage());
            decision = AgentDecision.idleFallback(e.getMessage());
        } catch (Exception e) {
            log.error("Agent {} 决策链路异常，降级 IDLE", agent.getName(), e);
            decision = AgentDecision.idleFallback("未预期异常: " + e.getMessage());
        }

        actionExecutor.execute(agent, decision, buildings, time);
        memoryOrchestrator.afterDecision(agent, perception, decision, time);
        applyRelations(agent, allAgents, decision, time);
        recordCot(agent, perception, memories, decision, llmResult, time);

        log.info("Agent {} 决策: action={}, loc={}, target={}, latency={}ms, tokens={}, fallback={}",
                agent.getName(),
                decision.getAction(),
                decision.getTargetLocation(),
                decision.getTargetAgent(),
                llmResult == null ? null : llmResult.getLatencyMs(),
                llmResult == null ? null : llmResult.getTotalTokens(),
                decision.isFallback());
    }

    private void applyRelations(Agent agent, List<Agent> allAgents, AgentDecision decision, GameTime time) {
        try {
            if (decision.getImpressionUpdates() != null && !decision.getImpressionUpdates().isEmpty()) {
                relationshipService.applyUpdates(agent, allAgents, decision.getImpressionUpdates(), time);
            } else if ("TALK".equalsIgnoreCase(decision.getAction()) && decision.getTargetAgent() != null) {
                relationshipService.bumpOnTalk(agent, decision.getTargetAgent(), allAgents, time, decision.getSpeech());
            }
        } catch (Exception e) {
            log.warn("关系更新失败: {}", e.getMessage());
        }
    }

    private void recordCot(
            Agent agent,
            PerceptionContext perception,
            MemoryBundle memories,
            AgentDecision decision,
            LlmCallResult llmResult,
            GameTime time
    ) {
        List<String> retrieved = new ArrayList<>();
        if (memories != null && memories.retrievedMemories() != null) {
            for (ScoredMemory sm : memories.retrievedMemories()) {
                retrieved.add(String.format("[%.2f][%s/%d] %s",
                        sm.getScore(),
                        sm.getMemory().getMemoryType(),
                        sm.getMemory().getImportance(),
                        sm.getMemory().getContent()));
            }
        }

        cotLogService.append(CotTurnLog.builder()
                .agentId(agent.getId())
                .agentName(agent.getName())
                .tick(time.getTick())
                .timeDisplay(time.format())
                .perceptionSummary(buildPerceptionSummary(perception))
                .retrievedMemories(retrieved)
                .thought(decision.getThought())
                .action(decision.getAction())
                .targetLocation(decision.getTargetLocation())
                .targetAgent(decision.getTargetAgent())
                .speech(decision.getSpeech())
                .fallback(decision.isFallback())
                .error(decision.getFallbackReason())
                .latencyMs(llmResult == null ? null : llmResult.getLatencyMs())
                .promptTokens(llmResult == null ? null : llmResult.getPromptTokens())
                .completionTokens(llmResult == null ? null : llmResult.getCompletionTokens())
                .totalTokens(llmResult == null ? null : llmResult.getTotalTokens())
                .model(llmResult == null ? null : llmResult.getModel())
                .createdAtEpochMs(System.currentTimeMillis())
                .build());
    }

    private void recordMockCot(Agent agent, GameTime time) {
        cotLogService.append(CotTurnLog.builder()
                .agentId(agent.getId())
                .agentName(agent.getName())
                .tick(time.getTick())
                .timeDisplay(time.format())
                .perceptionSummary("（Mock 模式，无 LLM 感知链路）")
                .retrievedMemories(List.of())
                .thought(agent.getLastThought())
                .action(agent.getLastAction())
                .speech(agent.getLastSpeech())
                .fallback(false)
                .latencyMs(0L)
                .totalTokens(0)
                .model("mock")
                .createdAtEpochMs(System.currentTimeMillis())
                .build());
    }

    private static String buildPerceptionSummary(PerceptionContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("时间 ").append(ctx.getTimeDisplay())
                .append("，位于「").append(ctx.getCurrentAreaName()).append("」")
                .append(" (").append(ctx.getX()).append(',').append(ctx.getY()).append(')');
        if (ctx.getNearbyAgents() != null && !ctx.getNearbyAgents().isEmpty()) {
            sb.append("；附近：")
                    .append(ctx.getNearbyAgents().stream()
                            .map(NearbyAgentInfo::getName)
                            .collect(Collectors.joining("、")));
        }
        if (ctx.getNearbySpeeches() != null && !ctx.getNearbySpeeches().isEmpty()) {
            sb.append("；听到：");
            for (AreaSpeechEvent ev : ctx.getNearbySpeeches()) {
                sb.append(ev.getSpeakerName()).append("「").append(ev.getSpeech()).append("」 ");
            }
        }
        if (ctx.getWorldEvents() != null && !ctx.getWorldEvents().isEmpty()) {
            sb.append("；世界事件：");
            for (WorldEvent ev : ctx.getWorldEvents()) {
                sb.append(ev.getContent()).append("；");
            }
        }
        return sb.toString();
    }

    private static String summarizeRetrieved(MemoryBundle memories) {
        if (memories == null || memories.retrievedMemories() == null || memories.retrievedMemories().isEmpty()) {
            return "";
        }
        return memories.retrievedMemories().stream()
                .map(ScoredMemory::getMemory)
                .map(m -> "[" + m.getMemoryType() + "/" + m.getImportance() + "] " + m.getContent())
                .collect(Collectors.joining(" | "));
    }
}
