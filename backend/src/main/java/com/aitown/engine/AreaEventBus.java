package com.aitown.engine;

import com.aitown.model.AreaSpeechEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 区域对话事件总线：本 Tick 写入的话，下一 Tick 才能被其他 Agent 感知，
 * 从而实现多 Agent 连续对话，同时避免同 Tick 内顺序依赖。
 */
@Component
public class AreaEventBus {

    /** 上一 Tick 已固化、可供感知的事件 */
    private final List<AreaSpeechEvent> perceptible = new CopyOnWriteArrayList<>();

    /** 当前 Tick 正在收集的新事件 */
    private final List<AreaSpeechEvent> pending = new CopyOnWriteArrayList<>();

    public List<AreaSpeechEvent> getPerceptibleEvents() {
        return Collections.unmodifiableList(new ArrayList<>(perceptible));
    }

    public void publish(AreaSpeechEvent event) {
        pending.add(event);
    }

    /**
     * Tick 结束时调用：将本 Tick 对话推进为「可感知」，并清空缓冲。
     */
    public void advanceTick() {
        perceptible.clear();
        perceptible.addAll(pending);
        pending.clear();
    }

    public void clear() {
        perceptible.clear();
        pending.clear();
    }
}
