package com.aitown.controller;

import com.aitown.common.Result;
import com.aitown.model.WorldEvent;
import com.aitown.observability.WorldEventService;
import com.aitown.service.WorldService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 上帝视角：投放 / 查询全局世界事件。
 */
@RestController
@RequestMapping("/api/world/events")
@RequiredArgsConstructor
public class WorldEventController {

    private final WorldService worldService;
    private final WorldEventService worldEventService;

    @PostMapping
    public Result<WorldEvent> inject(@RequestBody InjectRequest req) {
        if (req == null || req.getContent() == null || req.getContent().isBlank()) {
            return Result.fail("事件内容不能为空");
        }
        WorldEvent event = worldService.injectWorldEvent(req.getContent(), req.getDurationTicks());
        return Result.ok("世界事件已投放", event);
    }

    @GetMapping
    public Result<Map<String, Object>> list() {
        Map<String, Object> data = new HashMap<>();
        data.put("active", worldEventService.getActiveEvents());
        data.put("history", worldEventService.getHistory());
        return Result.ok(data);
    }

    @Data
    public static class InjectRequest {
        @NotBlank
        private String content;
        private Integer durationTicks;
    }
}
