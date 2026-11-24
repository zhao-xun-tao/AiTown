package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游戏内世界时钟（与现实时间无关）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameTime {

    /** 从模拟开始累计的天数（从第 1 天起） */
    private int day;

    /** 小时 0-23 */
    private int hour;

    /** 分钟 0-59 */
    private int minute;

    /** 累计 Tick 次数 */
    private long tick;

    /** 格式化为「第 N 天 HH:mm」 */
    public String format() {
        return String.format("第 %d 天 %02d:%02d", day, hour, minute);
    }

    /**
     * 推进若干游戏内分钟，自动处理跨小时 / 跨天。
     */
    public void advanceMinutes(int minutes) {
        if (minutes <= 0) {
            return;
        }
        int total = hour * 60 + minute + minutes;
        day += total / (24 * 60);
        int rem = total % (24 * 60);
        hour = rem / 60;
        minute = rem % 60;
        tick++;
    }
}
