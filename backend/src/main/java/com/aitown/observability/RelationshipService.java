package com.aitown.observability;

import com.aitown.model.Agent;
import com.aitown.model.AgentRelation;
import com.aitown.model.GameTime;
import com.aitown.model.ImpressionUpdate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 间好感度 / 印象图谱（Redis 持久 + 内存缓存）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipService {

    private static final String KEY_PREFIX = "aitown:rel:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 内存缓存，避免每 Tick 频繁反序列化 */
    private final Map<String, AgentRelation> cache = new ConcurrentHashMap<>();

    public void ensureInitialized(List<Agent> agents) {
        for (Agent from : agents) {
            for (Agent to : agents) {
                if (from.getId().equals(to.getId())) {
                    continue;
                }
                getOrCreate(from, to);
            }
        }
    }

    public List<AgentRelation> listFrom(String fromAgentId) {
        List<AgentRelation> list = new ArrayList<>();
        for (AgentRelation r : cache.values()) {
            if (fromAgentId.equals(r.getFromAgentId())) {
                list.add(copy(r));
            }
        }
        // 若缓存空，尝试从 Redis 扫描（开发期规模小，直接扫 key）
        if (list.isEmpty()) {
            try {
                var keys = redisTemplate.keys(KEY_PREFIX + fromAgentId + ":*");
                if (keys != null) {
                    for (String key : keys) {
                        String json = redisTemplate.opsForValue().get(key);
                        if (json != null) {
                            AgentRelation r = objectMapper.readValue(json, AgentRelation.class);
                            cache.put(cacheKey(r.getFromAgentId(), r.getToAgentId()), r);
                            list.add(copy(r));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("读取关系失败: {}", e.getMessage());
            }
        }
        list.sort((a, b) -> Integer.compare(b.getFavorability(), a.getFavorability()));
        return list;
    }

    public void applyUpdates(
            Agent from,
            List<Agent> allAgents,
            List<ImpressionUpdate> updates,
            GameTime time
    ) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        for (ImpressionUpdate u : updates) {
            if (u.getTarget() == null || u.getTarget().isBlank()) {
                continue;
            }
            Agent to = allAgents.stream()
                    .filter(a -> a.getName().equals(u.getTarget()) || a.getName().contains(u.getTarget()))
                    .findFirst()
                    .orElse(null);
            if (to == null || to.getId().equals(from.getId())) {
                continue;
            }
            int delta = u.getDelta() == null ? 1 : u.getDelta();
            delta = Math.max(-10, Math.min(10, delta));
            AgentRelation rel = getOrCreate(from, to);
            int next = Math.max(0, Math.min(100, rel.getFavorability() + delta));
            rel.setFavorability(next);
            if (u.getNote() != null && !u.getNote().isBlank()) {
                rel.setImpression(u.getNote().trim());
            }
            if (rel.getHistory() == null) {
                rel.setHistory(new ArrayList<>());
            }
            rel.getHistory().add(0, AgentRelation.RelationChange.builder()
                    .tick(time.getTick())
                    .timeDisplay(time.format())
                    .delta(delta)
                    .favorabilityAfter(next)
                    .note(u.getNote())
                    .build());
            while (rel.getHistory().size() > 20) {
                rel.getHistory().remove(rel.getHistory().size() - 1);
            }
            persist(rel);
        }
    }

    /**
     * TALK 时若 LLM 未给 impressionUpdates，给对方轻微 +1 好感兜底。
     */
    public void bumpOnTalk(Agent from, String targetName, List<Agent> allAgents, GameTime time, String speech) {
        if (targetName == null || targetName.isBlank()) {
            return;
        }
        ImpressionUpdate u = ImpressionUpdate.builder()
                .target(targetName)
                .delta(1)
                .note(speech == null ? "进行了一次对话" : "对话：「" + truncate(speech, 40) + "」")
                .build();
        applyUpdates(from, allAgents, List.of(u), time);
    }

    private AgentRelation getOrCreate(Agent from, Agent to) {
        String key = cacheKey(from.getId(), to.getId());
        AgentRelation cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + from.getId() + ":" + to.getId());
            if (json != null) {
                AgentRelation loaded = objectMapper.readValue(json, AgentRelation.class);
                cache.put(key, loaded);
                return loaded;
            }
        } catch (Exception e) {
            log.debug("关系缓存未命中: {}", e.getMessage());
        }
        AgentRelation created = AgentRelation.builder()
                .fromAgentId(from.getId())
                .fromAgentName(from.getName())
                .toAgentId(to.getId())
                .toAgentName(to.getName())
                .favorability(50)
                .impression("尚无深刻印象")
                .history(new ArrayList<>())
                .build();
        persist(created);
        return created;
    }

    private void persist(AgentRelation rel) {
        cache.put(cacheKey(rel.getFromAgentId(), rel.getToAgentId()), rel);
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + rel.getFromAgentId() + ":" + rel.getToAgentId(),
                    objectMapper.writeValueAsString(rel));
        } catch (JsonProcessingException e) {
            log.warn("序列化关系失败: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("写入 Redis 关系失败: {}", e.getMessage());
        }
    }

    private static String cacheKey(String from, String to) {
        return from + "->" + to;
    }

    private static AgentRelation copy(AgentRelation r) {
        return AgentRelation.builder()
                .fromAgentId(r.getFromAgentId())
                .fromAgentName(r.getFromAgentName())
                .toAgentId(r.getToAgentId())
                .toAgentName(r.getToAgentName())
                .favorability(r.getFavorability())
                .impression(r.getImpression())
                .history(r.getHistory() == null ? List.of() : new ArrayList<>(r.getHistory()))
                .build();
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
