package com.aitown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Town 多智能体社会模拟沙盒 - 启动入口
 * <p>
 * Phase 1：内存态世界 + Mock Agent 决策 + WebSocket 广播，暂不接入 MySQL / Redis / 真实 LLM。
 */
@SpringBootApplication
@EnableScheduling
public class AiTownApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTownApplication.class, args);
    }
}
