package com.aitown.controller;

import com.aitown.common.Result;
import com.aitown.model.AgentRelation;
import com.aitown.model.CotTurnLog;
import com.aitown.observability.CotLogService;
import com.aitown.observability.RelationshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 科研可观测性接口：CoT 思维链 + 关系图谱。
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class ObservabilityController {

    private final CotLogService cotLogService;
    private final RelationshipService relationshipService;

    @GetMapping("/{agentId}/cot")
    public Result<List<CotTurnLog>> cot(@PathVariable String agentId) {
        return Result.ok(cotLogService.list(agentId));
    }

    @GetMapping("/{agentId}/relations")
    public Result<List<AgentRelation>> relations(@PathVariable String agentId) {
        return Result.ok(relationshipService.listFrom(agentId));
    }

    @GetMapping("/{agentId}/observability")
    public Result<Map<String, Object>> bundle(@PathVariable String agentId) {
        Map<String, Object> data = new HashMap<>();
        data.put("cot", cotLogService.list(agentId));
        data.put("relations", relationshipService.listFrom(agentId));
        return Result.ok(data);
    }
}
