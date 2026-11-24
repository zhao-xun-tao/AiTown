package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 感知上下文中的「附近居民」摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyAgentInfo {

    private String name;
    private String role;
    private String state;
    private String bubble;
    private int x;
    private int y;
    private int distance;
}
