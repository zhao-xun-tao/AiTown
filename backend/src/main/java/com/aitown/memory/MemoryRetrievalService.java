package com.aitown.memory;

import com.aitown.config.MemoryProperties;
import com.aitown.model.GameTime;
import com.aitown.model.ScoredMemory;
import com.aitown.persistence.entity.AgentMemoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 长期记忆检索：综合「时间衰减 + 重要性 + 关键词相关性」取 Top-K。
 * <p>
 * 对齐 Generative Agents 的 retrieval function（此处关键词相关替代 embedding 余弦相似度，便于无向量库落地）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRetrievalService {

    private final LongTermMemoryService longTermMemoryService;
    private final MemoryProperties memoryProperties;

    public List<ScoredMemory> retrieveTopK(String agentId, String query, GameTime now) {
        if (!memoryProperties.isEnabled()) {
            return List.of();
        }
        List<AgentMemoryEntity> candidates = longTermMemoryService.listRecentCandidates(agentId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<String> queryTokens = tokenize(query);
        List<ScoredMemory> scored = new ArrayList<>(candidates.size());
        for (AgentMemoryEntity m : candidates) {
            double recency = computeRecency(m, now);
            double importance = (m.getImportance() == null ? 5 : m.getImportance()) / 10.0;
            double relevance = computeRelevance(queryTokens, m.getContent());
            double score = memoryProperties.getWeightRecency() * recency
                    + memoryProperties.getWeightImportance() * importance
                    + memoryProperties.getWeightRelevance() * relevance;
            scored.add(ScoredMemory.builder()
                    .memory(m)
                    .recencyScore(recency)
                    .importanceScore(importance)
                    .relevanceScore(relevance)
                    .score(score)
                    .build());
        }

        List<ScoredMemory> top = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredMemory::getScore).reversed())
                .limit(memoryProperties.getRetrieveTopK())
                .collect(Collectors.toList());

        List<Long> ids = top.stream()
                .map(s -> s.getMemory().getId())
                .filter(id -> id != null)
                .toList();
        try {
            longTermMemoryService.touchAccessed(ids);
        } catch (Exception e) {
            log.warn("更新 last_accessed_at 失败: {}", e.getMessage());
        }
        return top;
    }

    /**
     * recency = λ ^ gameHoursAgo ，λ 接近 1 时衰减缓慢。
     */
    double computeRecency(AgentMemoryEntity m, GameTime now) {
        double hoursAgo = Math.max(0, gameHours(now) - gameHoursOf(m));
        double lambda = memoryProperties.getRecencyDecayLambda();
        if (lambda <= 0 || lambda > 1) {
            lambda = 0.995;
        }
        return Math.pow(lambda, hoursAgo);
    }

    /**
     * 关键词相关性：基于字符 bigram Jaccard（对中文较稳，无需分词器）。
     */
    double computeRelevance(Set<String> queryTokens, String content) {
        if (queryTokens.isEmpty() || content == null || content.isBlank()) {
            return 0;
        }
        Set<String> doc = tokenize(content);
        if (doc.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String t : queryTokens) {
            if (doc.contains(t)) {
                inter++;
            }
        }
        int union = queryTokens.size() + doc.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        // 英文/数字词
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9\\u4e00-\\u9fff]+")) {
            if (w.length() >= 2) {
                tokens.add(w);
            }
        }
        // 中文 bigram
        StringBuilder han = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fff) {
                han.append(c);
            }
        }
        String h = han.toString();
        for (int i = 0; i + 1 < h.length(); i++) {
            tokens.add(h.substring(i, i + 2));
        }
        return tokens;
    }

    private double gameHours(GameTime t) {
        // 游戏时钟已包含 tick 推进结果，直接用 day/hour/minute
        return (t.getDay() - 1) * 24.0 + t.getHour() + t.getMinute() / 60.0;
    }

    private double gameHoursOf(AgentMemoryEntity m) {
        int day = m.getGameDay() == null ? 1 : m.getGameDay();
        int hour = m.getGameHour() == null ? 0 : m.getGameHour();
        int minute = m.getGameMinute() == null ? 0 : m.getGameMinute();
        return (day - 1) * 24.0 + hour + minute / 60.0;
    }
}
