package com.aitown.memory;

import com.aitown.config.MemoryProperties;
import com.aitown.model.WorkingMemoryItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 短期工作记忆（Redis）：每个 Agent 最近 N 条感知/对话事件。
 * Key: aitown:wm:{agentId} ，List 结构，左侧最新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingMemoryService {

    private static final String KEY_PREFIX = "aitown:wm:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryProperties memoryProperties;

    public void push(String agentId, WorkingMemoryItem item) {
        if (!memoryProperties.isEnabled()) {
            return;
        }
        try {
            String key = KEY_PREFIX + agentId;
            String json = objectMapper.writeValueAsString(item);
            redisTemplate.opsForList().leftPush(key, json);
            redisTemplate.opsForList().trim(key, 0, memoryProperties.getWorkingMemorySize() - 1L);
        } catch (Exception e) {
            log.error("写入短期工作记忆失败 agentId={}: {}", agentId, e.getMessage());
        }
    }

    /**
     * 返回按时间从新到旧的工作记忆列表。
     */
    public List<WorkingMemoryItem> listRecent(String agentId) {
        if (!memoryProperties.isEnabled()) {
            return List.of();
        }
        try {
            String key = KEY_PREFIX + agentId;
            List<String> raw = redisTemplate.opsForList().range(key, 0, memoryProperties.getWorkingMemorySize() - 1L);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<WorkingMemoryItem> items = new ArrayList<>(raw.size());
            for (String s : raw) {
                try {
                    items.add(objectMapper.readValue(s, WorkingMemoryItem.class));
                } catch (JsonProcessingException e) {
                    log.warn("解析工作记忆条目失败: {}", e.getMessage());
                }
            }
            return items;
        } catch (Exception e) {
            log.error("读取短期工作记忆失败 agentId={}: {}", agentId, e.getMessage());
            return List.of();
        }
    }

    public List<WorkingMemoryItem> listChronological(String agentId) {
        List<WorkingMemoryItem> recent = new ArrayList<>(listRecent(agentId));
        Collections.reverse(recent);
        return recent;
    }
}
