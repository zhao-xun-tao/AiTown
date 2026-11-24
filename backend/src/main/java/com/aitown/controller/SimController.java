package com.aitown.controller;

import com.aitown.common.Result;
import com.aitown.engine.WorldClockEngine;
import com.aitown.model.SimStatus;
import com.aitown.model.WorldState;
import com.aitown.service.WorldService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模拟控制接口：启动 / 暂停 / 单步 / 状态查询。
 */
@RestController
@RequestMapping("/api/sim")
@RequiredArgsConstructor
public class SimController {

    private final WorldClockEngine clockEngine;
    private final WorldService worldService;

    @PostMapping("/start")
    public Result<Map<String, Object>> start() {
        clockEngine.start();
        return Result.ok("模拟已启动", Map.of(
                "simStatus", SimStatus.RUNNING.name(),
                "running", true
        ));
    }

    @PostMapping("/pause")
    public Result<Map<String, Object>> pause() {
        clockEngine.pause();
        return Result.ok("模拟已暂停", Map.of(
                "simStatus", SimStatus.PAUSED.name(),
                "running", false
        ));
    }

    /** 单步推进并返回最新世界快照 */
    @PostMapping("/step")
    public Result<WorldState> step() {
        clockEngine.step();
        return Result.ok("已推进 1 Tick", worldService.snapshot());
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        WorldState snap = worldService.snapshot();
        return Result.ok(Map.of(
                "simStatus", snap.getSimStatus().name(),
                "running", clockEngine.isRunning(),
                "timeDisplay", snap.getTimeDisplay(),
                "tick", snap.getGameTime().getTick()
        ));
    }
}
