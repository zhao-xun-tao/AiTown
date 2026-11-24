package com.aitown.memory;

import com.aitown.engine.AgentDecisionParser;
import com.aitown.llm.LlmClient;
import com.aitown.llm.LlmException;
import com.aitown.model.Agent;
import com.aitown.model.GameTime;
import com.aitown.model.MemoryType;
import com.aitown.persistence.entity.AgentMemoryEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 反思机制：当累计重要性超过阈值时，让 LLM 从近期记忆提炼高层次认知并写回记忆库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionService {

    private final LlmClient llmClient;
    private final LongTermMemoryService longTermMemoryService;
    private final ReflectionTracker reflectionTracker;
    private final ObjectMapper objectMapper;

    /**
     * 若达到阈值则触发反思；失败只记日志，不影响模拟主循环。
     *
     * @return 新写入的反思记忆列表
     */
    public List<AgentMemoryEntity> maybeReflect(Agent agent, GameTime time) {
        if (!reflectionTracker.shouldReflect(agent.getId())) {
            return List.of();
        }

        long sinceTick = reflectionTracker.getLastReflectionTick(agent.getId());
        List<AgentMemoryEntity> evidence = longTermMemoryService.listHighImportanceSince(
                agent.getId(), sinceTick, 20);
        if (evidence.isEmpty()) {
            reflectionTracker.resetAfterReflection(agent.getId(), time.getTick());
            return List.of();
        }

        try {
            List<ReflectionItem> items = askLlmForReflections(agent, evidence);
            List<AgentMemoryEntity> saved = new ArrayList<>();
            for (ReflectionItem item : items) {
                if (item.getInsight() == null || item.getInsight().isBlank()) {
                    continue;
                }
                int imp = item.getImportance() == null ? 7 : item.getImportance();
                AgentMemoryEntity entity = longTermMemoryService.save(
                        agent.getId(),
                        agent.getName(),
                        MemoryType.REFLECTION,
                        item.getInsight().trim(),
                        imp,
                        time
                );
                if (entity != null) {
                    saved.add(entity);
                }
            }
            reflectionTracker.resetAfterReflection(agent.getId(), time.getTick());
            log.info("Agent {} 完成反思，写入 {} 条 REFLECTION", agent.getName(), saved.size());
            return saved;
        } catch (Exception e) {
            log.error("Agent {} 反思失败（不影响模拟）: {}", agent.getName(), e.getMessage());
            // 失败也重置，避免每个 Tick 反复打爆 LLM
            reflectionTracker.resetAfterReflection(agent.getId(), time.getTick());
            return List.of();
        }
    }

    private List<ReflectionItem> askLlmForReflections(Agent agent, List<AgentMemoryEntity> evidence)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是居民「").append(agent.getName()).append("」近期较重要的记忆：\n");
        for (int i = 0; i < evidence.size(); i++) {
            AgentMemoryEntity m = evidence.get(i);
            sb.append(i + 1).append(". [").append(m.getMemoryType()).append("][重要性")
                    .append(m.getImportance()).append("] ")
                    .append(m.getContent()).append('\n');
        }
        sb.append("""
                
                请提炼 1~3 条高层次认知（关于自我、他人关系或生活规律）。
                只输出 JSON 数组，不要 Markdown：
                [{"insight":"反思结论","importance":8}]
                importance 为 1-10 整数。
                """);

        String system = "你是 Generative Agents 的反思模块，负责从具体事件中抽象出稳定认知。只输出 JSON。";
        String raw = llmClient.chat(system, sb.toString());
        return parseReflections(raw);
    }

    List<ReflectionItem> parseReflections(String raw) throws Exception {
        String json = AgentDecisionParser.extractJsonObject(raw.trim());
        // 也可能是数组
        String payload = raw.trim();
        if (payload.contains("```")) {
            int start = payload.indexOf("```");
            int end = payload.lastIndexOf("```");
            if (end > start) {
                payload = payload.substring(start + 3, end).trim();
                if (payload.regionMatches(true, 0, "json", 0, 4)) {
                    payload = payload.substring(4).trim();
                }
            }
        }
        int arrStart = payload.indexOf('[');
        int arrEnd = payload.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            payload = payload.substring(arrStart, arrEnd + 1);
            JsonNode node = objectMapper.readTree(payload);
            List<ReflectionItem> list = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode n : node) {
                    list.add(objectMapper.treeToValue(n, ReflectionItem.class));
                }
            }
            return list;
        }
        if (json != null) {
            ReflectionItem one = objectMapper.readValue(json, ReflectionItem.class);
            return List.of(one);
        }
        throw new LlmException("反思结果无法解析为 JSON");
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReflectionItem {
        private String insight;
        private Integer importance;
    }
}
