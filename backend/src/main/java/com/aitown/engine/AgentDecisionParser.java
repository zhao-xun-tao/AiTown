package com.aitown.engine;

import com.aitown.model.AgentDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 从 LLM 原始文本中容错提取 AgentDecision JSON。
 * 支持：纯 JSON、Markdown 代码块包裹、前后夹杂说明文字。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDecisionParser {

    private final ObjectMapper objectMapper;

    public AgentDecision parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AgentDecision.idleFallback("LLM 返回空文本");
        }

        String json = extractJsonObject(raw.trim());
        if (json == null) {
            log.warn("无法从 LLM 输出中提取 JSON: {}", truncate(raw));
            return AgentDecision.idleFallback("无法提取 JSON");
        }

        try {
            AgentDecision decision = objectMapper.readValue(json, AgentDecision.class);
            if (decision.getAction() == null || decision.getAction().isBlank()) {
                decision.setAction("IDLE");
            }
            // 规范化空字符串为 null
            decision.setTargetLocation(blankToNull(decision.getTargetLocation()));
            decision.setTargetAgent(blankToNull(decision.getTargetAgent()));
            decision.setSpeech(blankToNull(decision.getSpeech()));
            if (decision.getThought() == null || decision.getThought().isBlank()) {
                decision.setThought("（无内心独白）");
            }
            return decision;
        } catch (Exception e) {
            log.warn("JSON 反序列化失败: {} | raw={}", e.getMessage(), truncate(raw));
            return AgentDecision.idleFallback("JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 提取第一个完整的 {...} 对象（简单括号计数，足够处理常见 LLM 输出）。
     */
    static String extractJsonObject(String text) {
        // 去掉 ```json ... ``` 包裹
        String cleaned = text;
        if (cleaned.contains("```")) {
            int start = cleaned.indexOf("```");
            int end = cleaned.lastIndexOf("```");
            if (end > start) {
                String block = cleaned.substring(start + 3, end).trim();
                if (block.regionMatches(true, 0, "json", 0, 4)) {
                    block = block.substring(4).trim();
                }
                cleaned = block;
            }
        }

        int first = cleaned.indexOf('{');
        if (first < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = first; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return cleaned.substring(first, i + 1);
                }
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t) || "none".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
