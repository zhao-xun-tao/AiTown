package com.aitown.engine;

import com.aitown.model.AgentDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDecisionParserTest {

    private final AgentDecisionParser parser = new AgentDecisionParser(new ObjectMapper());

    @Test
    void parsesCleanJson() {
        String raw = """
                {"thought":"去喝杯咖啡","action":"MOVE","targetLocation":"咖啡馆","targetAgent":null,"speech":null}
                """;
        AgentDecision d = parser.parse(raw);
        assertFalse(d.isFallback());
        assertEquals("MOVE", d.getAction());
        assertEquals("咖啡馆", d.getTargetLocation());
    }

    @Test
    void parsesMarkdownFencedJson() {
        String raw = """
                好的，这是我的决策：
                ```json
                {"thought":"打招呼","action":"TALK","targetLocation":null,"targetAgent":"林悦","speech":"嗨！"}
                ```
                """;
        AgentDecision d = parser.parse(raw);
        assertEquals("TALK", d.getAction());
        assertEquals("林悦", d.getTargetAgent());
        assertEquals("嗨！", d.getSpeech());
    }

    @Test
    void fallsBackOnGarbage() {
        AgentDecision d = parser.parse("这不是 JSON");
        assertTrue(d.isFallback());
        assertEquals("IDLE", d.getAction());
    }
}
