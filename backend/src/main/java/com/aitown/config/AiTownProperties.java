package com.aitown.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 从 application.yml 的 aitown.* 读取模拟参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aitown")
public class AiTownProperties {

    /** 自动 Tick 间隔（毫秒） */
    private long tickIntervalMs = 2000L;

    /** 每次 Tick 推进的游戏内分钟数 */
    private int minutesPerTick = 10;

    private int startHour = 8;

    private int startMinute = 0;
}
