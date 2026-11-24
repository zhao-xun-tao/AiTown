package com.aitown.memory;

import com.aitown.config.MemoryProperties;
import com.aitown.model.GameTime;
import com.aitown.persistence.entity.AgentMemoryEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryRetrievalServiceTest {

    @Test
    void ranksImportantRelevantRecentHigher() {
        MemoryProperties props = new MemoryProperties();
        props.setEnabled(true);
        props.setRetrieveTopK(2);
        props.setWeightRecency(1);
        props.setWeightImportance(1);
        props.setWeightRelevance(1);
        props.setRecencyDecayLambda(0.995);

        LongTermMemoryService longTerm = mock(LongTermMemoryService.class);
        when(longTerm.listRecentCandidates("a1")).thenReturn(List.of(
                mem(1L, "今天去了咖啡馆见林悦聊天", 9, 1, 8, 0),
                mem(2L, "路过公园", 2, 1, 8, 0),
                mem(3L, "整理书桌", 1, 1, 7, 0)
        ));

        MemoryRetrievalService svc = new MemoryRetrievalService(longTerm, props);
        GameTime now = GameTime.builder().day(1).hour(8).minute(30).tick(3).build();
        var top = svc.retrieveTopK("a1", "林悦 咖啡馆 聊天", now);

        assertEquals(2, top.size());
        assertEquals(1L, top.get(0).getMemory().getId());
        assertTrue(top.get(0).getScore() >= top.get(1).getScore());
    }

    @Test
    void tokenizeProducesChineseBigrams() {
        Set<String> tokens = MemoryRetrievalService.tokenize("林悦在咖啡馆");
        assertTrue(tokens.contains("林悦") || tokens.contains("悦在") || tokens.contains("咖啡"));
    }

    private static AgentMemoryEntity mem(long id, String content, int importance, int day, int hour, int minute) {
        return AgentMemoryEntity.builder()
                .id(id)
                .agentId("a1")
                .agentName("测试")
                .memoryType("OBSERVATION")
                .content(content)
                .importance(importance)
                .gameDay(day)
                .gameHour(hour)
                .gameMinute(minute)
                .gameTick(1L)
                .build();
    }
}
