package com.aitown.controller;

import com.aitown.common.Result;
import com.aitown.model.WorldState;
import com.aitown.service.WorldService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 世界状态查询接口。
 */
@RestController
@RequestMapping("/api/world")
@RequiredArgsConstructor
public class WorldController {

    private final WorldService worldService;

    /** 获取当前完整世界快照（地图、建筑、居民、时间） */
    @GetMapping("/state")
    public Result<WorldState> state() {
        return Result.ok(worldService.snapshot());
    }
}
