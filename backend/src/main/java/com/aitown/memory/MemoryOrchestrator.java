package com.aitown.memory;

import com.aitown.config.MemoryProperties;
import com.aitown.model.Agent;
import com.aitown.model.AgentDecision;
import com.aitown.model.GameTime;
import com.aitown.model.MemoryType;
import com.aitown.model.PerceptionContext;
import com.aitown.model.ScoredMemory;
import com.aitown.model.WorkingMemoryItem;
import com.aitown.persistence.entity.AgentMemoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆编排：决策前检索、决策后写入短期/长期记忆，并按需触发反思。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryOrchestrator {

    private final MemoryProperties memoryProperties;
    private final WorkingMemoryService workingMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final MemoryRetrievalService retrievalService;
    private final ReflectionTracker reflectionTracker;
    private final ReflectionService reflectionService;

    /**
     * 决策前：组装检索查询 → Top-K 长期记忆 + 短期工作记忆。
     */
    public MemoryBundle prepareForDecision(Agent agent, PerceptionContext perception, GameTime time) {
        if (!memoryProperties.isEnabled()) {
            return MemoryBundle.empty();
        }
        String query = buildRetrievalQuery(agent, perception);
        List<ScoredMemory> longTerm = retrievalService.retrieveTopK(agent.getId(), query, time);
        List<WorkingMemoryItem> working = workingMemoryService.listRecent(agent.getId());
        return new MemoryBundle(working, longTerm);
    }

    /**
     * 决策后：写入工作记忆 + 长期记忆（带 LLM 顺便打的重要性），必要时反思。
     */
    public void afterDecision(
            Agent agent,
            PerceptionContext perception,
            AgentDecision decision,
            GameTime time
    ) {
        if (!memoryProperties.isEnabled() || decision.isFallback()) {
            // fallback 也写入一条低重要性观察，保持连贯；但仍跳过反思轰炸
            if (memoryProperties.isEnabled()) {
                writePerceptionWorkingMemories(agent, perception, time);
            }
            return;
        }

        try {
            writePerceptionWorkingMemories(agent, perception, time);

            MemoryType type = resolveMemoryType(decision);
            String content = resolveMemoryContent(agent, perception, decision);
            int importance = resolveImportance(decision);

            // 短期：本回合决策摘要
            workingMemoryService.push(agent.getId(), WorkingMemoryItem.builder()
                    .tick(time.getTick())
                    .timeDisplay(time.format())
                    .type(type.name())
                    .content(content)
                    .createdAtEpochMs(System.currentTimeMillis())
                    .build());

            // 长期
            AgentMemoryEntity saved = longTermMemoryService.save(
                    agent.getId(), agent.getName(), type, content, importance, time);
            if (saved != null) {
                reflectionTracker.addImportance(agent.getId(), importance);
            }

            // 若听到对话，额外记一条 OBSERVATION（重要性略低）
            if (perception.getNearbySpeeches() != null) {
                perception.getNearbySpeeches().forEach(ev -> {
                    String heard = "听到" + ev.getSpeakerName() + "说：「" + ev.getSpeech() + "」";
                    workingMemoryService.push(agent.getId(), WorkingMemoryItem.builder()
                            .tick(time.getTick())
                            .timeDisplay(time.format())
                            .type(MemoryType.DIALOGUE.name())
                            .content(heard)
                            .createdAtEpochMs(System.currentTimeMillis())
                            .build());
                });
            }

            reflectionService.maybeReflect(agent, time);
        } catch (Exception e) {
            log.error("Agent {} 记忆写入失败（不影响模拟）: {}", agent.getName(), e.getMessage());
        }
    }

    private void writePerceptionWorkingMemories(Agent agent, PerceptionContext perception, GameTime time) {
        String areaNote = "位于「" + perception.getCurrentAreaName() + "」";
        if (perception.getNearbyAgents() != null && !perception.getNearbyAgents().isEmpty()) {
            String names = perception.getNearbyAgents().stream()
                    .map(n -> n.getName())
                    .collect(Collectors.joining("、"));
            areaNote += "，附近有：" + names;
        }
        workingMemoryService.push(agent.getId(), WorkingMemoryItem.builder()
                .tick(time.getTick())
                .timeDisplay(time.format())
                .type(MemoryType.OBSERVATION.name())
                .content(areaNote)
                .createdAtEpochMs(System.currentTimeMillis())
                .build());
    }

    private String buildRetrievalQuery(Agent agent, PerceptionContext perception) {
        StringBuilder q = new StringBuilder();
        q.append(agent.getName()).append(' ').append(agent.getRole()).append(' ');
        q.append(perception.getCurrentAreaName()).append(' ');
        if (perception.getNearbyAgents() != null) {
            perception.getNearbyAgents().forEach(n -> q.append(n.getName()).append(' '));
        }
        if (perception.getNearbySpeeches() != null) {
            perception.getNearbySpeeches().forEach(s ->
                    q.append(s.getSpeakerName()).append(' ').append(s.getSpeech()).append(' '));
        }
        if (agent.getLastThought() != null) {
            q.append(agent.getLastThought());
        }
        return q.toString();
    }

    private MemoryType resolveMemoryType(AgentDecision decision) {
        if (decision.getMemoryType() != null && !decision.getMemoryType().isBlank()) {
            return MemoryType.parseSafe(decision.getMemoryType(),
                    "TALK".equalsIgnoreCase(decision.getAction()) ? MemoryType.DIALOGUE : MemoryType.OBSERVATION);
        }
        return "TALK".equalsIgnoreCase(decision.getAction()) ? MemoryType.DIALOGUE : MemoryType.OBSERVATION;
    }

    private String resolveMemoryContent(Agent agent, PerceptionContext perception, AgentDecision decision) {
        if (decision.getMemoryNote() != null && !decision.getMemoryNote().isBlank()) {
            return decision.getMemoryNote().trim();
        }
        // 兜底：用 thought / speech 拼一条
        if ("TALK".equalsIgnoreCase(decision.getAction()) && decision.getSpeech() != null) {
            String target = decision.getTargetAgent() == null ? "附近的人" : decision.getTargetAgent();
            return "我对" + target + "说：「" + decision.getSpeech() + "」";
        }
        if (decision.getThought() != null && !decision.getThought().isBlank()) {
            return "在「" + perception.getCurrentAreaName() + "」：" + decision.getThought();
        }
        return agent.getName() + "在「" + perception.getCurrentAreaName() + "」行动=" + decision.getAction();
    }

    private int resolveImportance(AgentDecision decision) {
        if (decision.getMemoryImportance() != null) {
            return decision.getMemoryImportance();
        }
        // 启发式兜底
        return switch (decision.actionType()) {
            case TALK -> 6;
            case MOVE -> 3;
            default -> 2;
        };
    }

    /**
     * 决策时可用的记忆打包。
     */
    public record MemoryBundle(
            List<WorkingMemoryItem> workingMemories,
            List<ScoredMemory> retrievedMemories
    ) {
        public static MemoryBundle empty() {
            return new MemoryBundle(List.of(), List.of());
        }
    }
}
