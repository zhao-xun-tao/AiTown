package com.aitown.observability;

import com.aitown.model.GameTime;
import com.aitown.model.WorldEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 上帝视角世界事件：注入后在若干 Tick 内对所有 Agent 可见。
 */
@Slf4j
@Service
public class WorldEventService {

    /** 默认持续 6 个 Tick（约 1 游戏小时，若 minutesPerTick=10） */
    public static final int DEFAULT_DURATION_TICKS = 6;

    private final List<WorldEvent> active = new CopyOnWriteArrayList<>();
    private final List<WorldEvent> history = new CopyOnWriteArrayList<>();

    public synchronized WorldEvent inject(String content, GameTime time, int durationTicks) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("事件内容不能为空");
        }
        int dur = durationTicks <= 0 ? DEFAULT_DURATION_TICKS : durationTicks;
        WorldEvent event = WorldEvent.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .content(text)
                .createdTick(time.getTick())
                .createdTimeDisplay(time.format())
                .remainingTicks(dur)
                .durationTicks(dur)
                .build();
        active.add(event);
        history.add(0, event);
        while (history.size() > 50) {
            history.remove(history.size() - 1);
        }
        log.info("上帝事件已投放: [{}] {}", event.getId(), event.getContent());
        return event;
    }

    public List<WorldEvent> getActiveEvents() {
        return new ArrayList<>(active);
    }

    public List<WorldEvent> getHistory() {
        return new ArrayList<>(history);
    }

    /** 每个 Tick 结束时衰减剩余回合 */
    public synchronized void onTickEnd() {
        List<WorldEvent> next = new ArrayList<>();
        for (WorldEvent e : active) {
            int rem = e.getRemainingTicks() - 1;
            if (rem > 0) {
                e.setRemainingTicks(rem);
                next.add(e);
            }
        }
        active.clear();
        active.addAll(next);
    }
}
