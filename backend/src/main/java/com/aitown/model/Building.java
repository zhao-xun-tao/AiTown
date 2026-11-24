package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能建筑区域：用轴对齐矩形描述在网格上的占用范围。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Building {

    /** 建筑唯一标识 */
    private String id;

    /** 显示名称 */
    private String name;

    /** 区域类型 */
    private ZoneType type;

    /** 左上角网格 X（含） */
    private int x;

    /** 左上角网格 Y（含） */
    private int y;

    /** 宽度（格） */
    private int width;

    /** 高度（格） */
    private int height;

    /** 是否包含指定网格坐标 */
    public boolean contains(int gx, int gy) {
        return gx >= x && gx < x + width && gy >= y && gy < y + height;
    }
}
