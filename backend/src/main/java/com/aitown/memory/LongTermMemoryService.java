package com.aitown.memory;

import com.aitown.config.MemoryProperties;
import com.aitown.mapper.AgentMemoryMapper;
import com.aitown.model.GameTime;
import com.aitown.model.MemoryType;
import com.aitown.persistence.entity.AgentMemoryEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 长期记忆流（MySQL）：增删查改 agent_memory。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private final AgentMemoryMapper memoryMapper;
    private final MemoryProperties memoryProperties;

    public AgentMemoryEntity save(
            String agentId,
            String agentName,
            MemoryType type,
            String content,
            int importance,
            GameTime time
    ) {
        if (!memoryProperties.isEnabled()) {
            return null;
        }
        int clamped = Math.max(1, Math.min(10, importance));
        AgentMemoryEntity entity = AgentMemoryEntity.builder()
                .agentId(agentId)
                .agentName(agentName)
                .memoryType(type.name())
                .content(content)
                .importance(clamped)
                .gameDay(time.getDay())
                .gameHour(time.getHour())
                .gameMinute(time.getMinute())
                .gameTick(time.getTick())
                .createdAt(LocalDateTime.now())
                .build();
        memoryMapper.insert(entity);
        log.debug("长期记忆写入 agent={} type={} imp={} id={}", agentName, type, clamped, entity.getId());
        return entity;
    }

    /**
     * 取该 Agent 最近的候选记忆，供检索打分。
     */
    public List<AgentMemoryEntity> listRecentCandidates(String agentId) {
        if (!memoryProperties.isEnabled()) {
            return List.of();
        }
        return memoryMapper.selectList(new LambdaQueryWrapper<AgentMemoryEntity>()
                .eq(AgentMemoryEntity::getAgentId, agentId)
                .orderByDesc(AgentMemoryEntity::getGameTick)
                .orderByDesc(AgentMemoryEntity::getId)
                .last("LIMIT " + memoryProperties.getRetrieveCandidateLimit()));
    }

    public List<AgentMemoryEntity> listByAgent(String agentId, int limit) {
        if (!memoryProperties.isEnabled()) {
            return List.of();
        }
        int safe = Math.max(1, Math.min(limit, 200));
        return memoryMapper.selectList(new LambdaQueryWrapper<AgentMemoryEntity>()
                .eq(AgentMemoryEntity::getAgentId, agentId)
                .orderByDesc(AgentMemoryEntity::getGameTick)
                .orderByDesc(AgentMemoryEntity::getId)
                .last("LIMIT " + safe));
    }

    public void touchAccessed(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        memoryMapper.update(null, new LambdaUpdateWrapper<AgentMemoryEntity>()
                .in(AgentMemoryEntity::getId, ids)
                .set(AgentMemoryEntity::getLastAccessedAt, LocalDateTime.now()));
    }

    public List<AgentMemoryEntity> listHighImportanceSince(String agentId, long sinceTickExclusive, int limit) {
        if (!memoryProperties.isEnabled()) {
            return Collections.emptyList();
        }
        return memoryMapper.selectList(new LambdaQueryWrapper<AgentMemoryEntity>()
                .eq(AgentMemoryEntity::getAgentId, agentId)
                .gt(AgentMemoryEntity::getGameTick, sinceTickExclusive)
                .ne(AgentMemoryEntity::getMemoryType, MemoryType.REFLECTION.name())
                .orderByDesc(AgentMemoryEntity::getImportance)
                .orderByDesc(AgentMemoryEntity::getGameTick)
                .last("LIMIT " + limit));
    }
}
