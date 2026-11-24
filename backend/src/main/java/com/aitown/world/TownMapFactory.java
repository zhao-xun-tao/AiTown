package com.aitown.world;

import com.aitown.model.Building;
import com.aitown.model.ZoneType;

import java.util.ArrayList;
import java.util.List;

/**
 * 构建 20x20 小镇静态地图上的功能建筑区域。
 * <p>
 * 布局示意（坐标原点在左上，X 向右、Y 向下）：
 * <pre>
 *   住宅(0,0)     图书馆(7,0)      住宅(14,0)
 *   公园(0,7)     广场(7,7)        咖啡馆(14,7)
 *   住宅(0,14)    公园(7,14)       住宅(14,14)
 * </pre>
 */
public final class TownMapFactory {

    public static final int MAP_SIZE = 20;

    private TownMapFactory() {
    }

    public static List<Building> createBuildings() {
        List<Building> buildings = new ArrayList<>();

        buildings.add(Building.builder()
                .id("plaza")
                .name("中央广场")
                .type(ZoneType.PLAZA)
                .x(7).y(7).width(6).height(6)
                .build());

        buildings.add(Building.builder()
                .id("cafe")
                .name("林悦咖啡馆")
                .type(ZoneType.CAFE)
                .x(14).y(7).width(5).height(5)
                .build());

        buildings.add(Building.builder()
                .id("library")
                .name("静心图书馆")
                .type(ZoneType.LIBRARY)
                .x(7).y(0).width(6).height(5)
                .build());

        buildings.add(Building.builder()
                .id("park-west")
                .name("西侧公园")
                .type(ZoneType.PARK)
                .x(0).y(7).width(5).height(5)
                .build());

        buildings.add(Building.builder()
                .id("park-south")
                .name("南侧公园")
                .type(ZoneType.PARK)
                .x(7).y(14).width(6).height(5)
                .build());

        buildings.add(Building.builder()
                .id("house-nw")
                .name("西北住宅")
                .type(ZoneType.HOUSE)
                .x(0).y(0).width(5).height(5)
                .build());

        buildings.add(Building.builder()
                .id("house-ne")
                .name("东北住宅")
                .type(ZoneType.HOUSE)
                .x(14).y(0).width(5).height(5)
                .build());

        buildings.add(Building.builder()
                .id("house-sw")
                .name("西南住宅")
                .type(ZoneType.HOUSE)
                .x(0).y(14).width(5).height(5)
                .build());

        buildings.add(Building.builder()
                .id("house-se")
                .name("东南住宅")
                .type(ZoneType.HOUSE)
                .x(14).y(14).width(5).height(5)
                .build());

        return buildings;
    }
}
