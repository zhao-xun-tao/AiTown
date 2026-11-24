package com.aitown.engine;

import com.aitown.config.AiTownProperties;
import com.aitown.model.SimStatus;
import com.aitown.service.WorldService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 世界时钟引擎：支持启动、暂停、单步推进。
 * <p>
 * 自动模式下按 {@link AiTownProperties#getTickIntervalMs()} 间隔触发；
 * LLM 调用较慢时用 fixedDelay + 互斥，避免 Tick 堆积。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldClockEngine {

    private final WorldService worldService;
    private final AiTownProperties properties;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> tickFuture;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean tickInFlight = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "world-clock");
            t.setDaemon(true);
            return t;
        });
        log.info("世界时钟引擎就绪，Tick 间隔={}ms，每 Tick +{} 分钟",
                properties.getTickIntervalMs(), properties.getMinutesPerTick());
    }

    @PreDestroy
    public void shutdown() {
        pause();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /** 开始自动推进 */
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        worldService.setSimStatus(SimStatus.RUNNING);
        long interval = properties.getTickIntervalMs();
        // fixedDelay：上一 Tick（含 LLM）结束后再等 interval，避免排队打爆 API
        tickFuture = scheduler.scheduleWithFixedDelay(this::safeTick, interval, interval, TimeUnit.MILLISECONDS);
        log.info("模拟已启动");
    }

    /** 暂停自动推进 */
    public synchronized void pause() {
        if (tickFuture != null) {
            tickFuture.cancel(false);
            tickFuture = null;
        }
        if (running.getAndSet(false)) {
            worldService.setSimStatus(SimStatus.PAUSED);
            log.info("模拟已暂停");
        }
    }

    /** 单步推进一次 Tick（不论当前是否在自动运行） */
    public synchronized void step() {
        worldService.tick();
        log.info("单步 Tick 完成");
    }

    public boolean isRunning() {
        return running.get();
    }

    private void safeTick() {
        if (!running.get()) {
            return;
        }
        if (!tickInFlight.compareAndSet(false, true)) {
            log.warn("上一 Tick 仍在执行，跳过本轮调度");
            return;
        }
        try {
            worldService.tick();
        } catch (Exception e) {
            // 模拟循环绝不能因单次异常崩溃
            log.error("自动 Tick 执行失败，已吞掉异常以保持引擎存活", e);
        } finally {
            tickInFlight.set(false);
        }
    }
}
