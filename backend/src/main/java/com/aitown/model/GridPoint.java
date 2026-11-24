package com.aitown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 网格坐标点（寻路路径节点） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridPoint {
    private int x;
    private int y;
}
