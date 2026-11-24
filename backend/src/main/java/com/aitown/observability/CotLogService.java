package com.aitown.observability;

import com.aitown.model.CotTurnLog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 内存环形缓冲：每个 Agent 保留最近 N 条 CoT 回合日志。
 */
@Service
public class CotLogService {

    private static final int MAX_PER_AGENT = 40;

    private final Map<String, Deque<CotTurnLog>> logs = new ConcurrentHashMap<>();

    public void append(CotTurnLog log) {
        if (log == null || log.getAgentId() == null) {
            return;
        }
        Deque<CotTurnLog> q = logs.computeIfAbsent(log.getAgentId(), k -> new ConcurrentLinkedDeque<>());
        q.addFirst(log);
        while (q.size() > MAX_PER_AGENT) {
            q.removeLast();
        }
    }

    /** 新到旧 */
    public List<CotTurnLog> list(String agentId) {
        Deque<CotTurnLog> q = logs.get(agentId);
        if (q == null) {
            return List.of();
        }
        return new ArrayList<>(q);
    }
}
