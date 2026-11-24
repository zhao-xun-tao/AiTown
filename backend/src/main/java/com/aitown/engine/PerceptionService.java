package com.aitown.engine;

import com.aitown.config.LlmProperties;
import com.aitown.model.Agent;
import com.aitown.model.AreaSpeechEvent;
import com.aitown.model.Building;
import com.aitown.model.GameTime;
import com.aitown.model.NearbyAgentInfo;
import com.aitown.model.PerceptionContext;
import com.aitown.observability.WorldEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 收集 Agent 周围环境信息，生成结构化感知上下文。
 */
@Service
@RequiredArgsConstructor
public class PerceptionService {

    private final LlmProperties llmProperties;
    private final AreaEventBus areaEventBus;
    private final WorldEventService worldEventService;

    public PerceptionContext build(
            Agent self,
            List<Agent> allAgents,
            List<Building> buildings,
            GameTime time
    ) {
        Building here = findBuilding(self.getX(), self.getY(), buildings);
        String areaName = here != null ? here.getName() : "街道";
        String areaType = here != null ? here.getType().name() : "ROAD";
        String areaKey = here != null ? here.getId() : "road";

        int radius = llmProperties.getPerceptionRadius();
        List<NearbyAgentInfo> nearby = new ArrayList<>();
        for (Agent other : allAgents) {
            if (other.getId().equals(self.getId())) {
                continue;
            }
            int dist = manhattan(self.getX(), self.getY(), other.getX(), other.getY());
            boolean sameArea = isSameArea(self, other, buildings);
            if (dist <= radius || sameArea) {
                nearby.add(NearbyAgentInfo.builder()
                        .name(other.getName())
                        .role(other.getRole())
                        .state(other.getState() != null ? other.getState().name() : "IDLE")
                        .bubble(other.getBubble())
                        .x(other.getX())
                        .y(other.getY())
                        .distance(dist)
                        .build());
            }
        }

        // 感知上一 Tick 的区域对话：同区域，或说话点在感知半径内
        List<AreaSpeechEvent> speeches = new ArrayList<>();
        for (AreaSpeechEvent ev : areaEventBus.getPerceptibleEvents()) {
            if (ev.getSpeakerId() != null && ev.getSpeakerId().equals(self.getId())) {
                continue;
            }
            int dist = manhattan(self.getX(), self.getY(), ev.getX(), ev.getY());
            boolean sameArea = areaKey.equals(ev.getAreaKey())
                    || (here != null && here.getName().equals(ev.getAreaName()));
            if (sameArea || dist <= radius) {
                speeches.add(ev);
            }
        }

        Set<String> locations = new LinkedHashSet<>();
        for (Building b : buildings) {
            locations.add(b.getName());
            // 同时提供简短别名，方便模型写「咖啡馆」「公园」
            locations.add(aliasOf(b));
        }

        return PerceptionContext.builder()
                .timeDisplay(time.format())
                .tick(time.getTick())
                .currentAreaName(areaName)
                .currentAreaType(areaType)
                .x(self.getX())
                .y(self.getY())
                .nearbyAgents(nearby)
                .nearbySpeeches(speeches)
                .knownLocations(new ArrayList<>(locations))
                .worldEvents(new ArrayList<>(worldEventService.getActiveEvents()))
                .build();
    }

    public Building findBuilding(int x, int y, List<Building> buildings) {
        return buildings.stream()
                .filter(b -> b.contains(x, y))
                .findFirst()
                .orElse(null);
    }

    public String areaKeyAt(int x, int y, List<Building> buildings) {
        Building b = findBuilding(x, y, buildings);
        return b != null ? b.getId() : "road";
    }

    public String areaNameAt(int x, int y, List<Building> buildings) {
        Building b = findBuilding(x, y, buildings);
        return b != null ? b.getName() : "街道";
    }

    private boolean isSameArea(Agent a, Agent b, List<Building> buildings) {
        Building ba = findBuilding(a.getX(), a.getY(), buildings);
        Building bb = findBuilding(b.getX(), b.getY(), buildings);
        if (ba == null || bb == null) {
            return false;
        }
        return ba.getId().equals(bb.getId());
    }

    private static int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private static String aliasOf(Building b) {
        return switch (b.getType()) {
            case CAFE -> "咖啡馆";
            case LIBRARY -> "图书馆";
            case PARK -> "公园";
            case PLAZA -> "广场";
            case HOUSE -> "住宅";
            default -> b.getName();
        };
    }
}
