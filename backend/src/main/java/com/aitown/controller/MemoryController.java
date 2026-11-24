package com.aitown.controller;

import com.aitown.common.Result;
import com.aitown.memory.LongTermMemoryService;
import com.aitown.memory.WorkingMemoryService;
import com.aitown.model.WorkingMemoryItem;
import com.aitown.persistence.entity.AgentMemoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆观察 API：供研究工作台查看短期/长期记忆流。
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class MemoryController {

    private final WorkingMemoryService workingMemoryService;
    private final LongTermMemoryService longTermMemoryService;

    @GetMapping("/{agentId}/memories")
    public Result<Map<String, Object>> memories(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "30") int longTermLimit
    ) {
        List<WorkingMemoryItem> working = workingMemoryService.listRecent(agentId);
        List<AgentMemoryEntity> longTerm = longTermMemoryService.listByAgent(agentId, longTermLimit);
        Map<String, Object> data = new HashMap<>();
        data.put("agentId", agentId);
        data.put("working", working);
        data.put("longTerm", longTerm);
        return Result.ok(data);
    }
}
