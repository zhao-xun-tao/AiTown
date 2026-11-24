package com.aitown.memory;

import com.aitown.config.MemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 追踪「自上次反思以来累计重要性」以及上次反思时的 gameTick（Redis）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReflectionTracker {

    private static final String IMP_KEY = "aitown:reflect:imp:";
    private static final String TICK_KEY = "aitown:reflect:tick:";

    private final StringRedisTemplate redisTemplate;
    private final MemoryProperties memoryProperties;

    public void addImportance(String agentId, int importance) {
        if (!memoryProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().increment(IMP_KEY + agentId, importance);
        } catch (Exception e) {
            log.error("累计反思重要性失败: {}", e.getMessage());
        }
    }

    public int getAccumulatedImportance(String agentId) {
        try {
            String v = redisTemplate.opsForValue().get(IMP_KEY + agentId);
            return v == null ? 0 : Integer.parseInt(v);
        } catch (Exception e) {
            return 0;
        }
    }

    public long getLastReflectionTick(String agentId) {
        try {
            String v = redisTemplate.opsForValue().get(TICK_KEY + agentId);
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            return 0L;
        }
    }

    public boolean shouldReflect(String agentId) {
        return getAccumulatedImportance(agentId) >= memoryProperties.getReflectionImportanceThreshold();
    }

    public void resetAfterReflection(String agentId, long currentTick) {
        try {
            redisTemplate.opsForValue().set(IMP_KEY + agentId, "0");
            redisTemplate.opsForValue().set(TICK_KEY + agentId, String.valueOf(currentTick));
        } catch (Exception e) {
            log.error("重置反思计数失败: {}", e.getMessage());
        }
    }
}
