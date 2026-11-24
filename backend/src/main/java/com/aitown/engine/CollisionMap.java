package com.aitown.engine;

import com.aitown.model.Building;
import com.aitown.model.ZoneType;
import com.aitown.world.TownMapFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 小镇碰撞网格：与前端 collision.ts 规则保持一致。
 * <p>
 * - 喷泉中心不可走
 * - 固定树位不可走
 * - HOUSE/CAFE/LIBRARY：南侧一排为门廊可走，其余 footprint 视为墙体
 * - PARK/PLAZA/道路其余可走
 */
public final class CollisionMap {

    private final boolean[][] blocked;
    private final int size;

    private CollisionMap(int size, boolean[][] blocked) {
        this.size = size;
        this.blocked = blocked;
    }

    public static CollisionMap fromBuildings(List<Building> buildings) {
        int n = TownMapFactory.MAP_SIZE;
        boolean[][] blocked = new boolean[n][n];

        // 中央喷泉 2x2
        for (int y = 9; y <= 10; y++) {
            for (int x = 9; x <= 10; x++) {
                blocked[y][x] = true;
            }
        }

        // 路边树（与前端装饰坐标对齐）
        int[][] trees = {{5, 9}, {12, 5}, {4, 12}, {15, 12}};
        for (int[] t : trees) {
            if (inBounds(t[0], t[1], n)) {
                blocked[t[1]][t[0]] = true;
            }
        }

        for (Building b : buildings) {
            if (b.getType() == ZoneType.PARK) {
                // 公园内默认树位
                markIfIn(blocked, b.getX() + 1, b.getY() + 1, n);
                markIfIn(blocked, b.getX() + b.getWidth() - 2, b.getY() + 2, n);
                continue;
            }
            if (b.getType() != ZoneType.HOUSE
                    && b.getType() != ZoneType.CAFE
                    && b.getType() != ZoneType.LIBRARY) {
                continue;
            }
            // 南侧一排门廊可走，其余为墙
            int doorY = b.getY() + b.getHeight() - 1;
            for (int gy = b.getY(); gy < b.getY() + b.getHeight(); gy++) {
                for (int gx = b.getX(); gx < b.getX() + b.getWidth(); gx++) {
                    if (gy < doorY && inBounds(gx, gy, n)) {
                        blocked[gy][gx] = true;
                    }
                }
            }
        }

        return new CollisionMap(n, blocked);
    }

    public int size() {
        return size;
    }

    public boolean isBlocked(int x, int y) {
        if (!inBounds(x, y, size)) {
            return true;
        }
        return blocked[y][x];
    }

    public boolean isWalkable(int x, int y) {
        return !isBlocked(x, y);
    }

    /** 若目标在墙内，吸附到同建筑南侧门廊最近可走格 */
    public int[] snapToWalkable(int x, int y, List<Building> buildings) {
        if (isWalkable(x, y)) {
            return new int[]{x, y};
        }
        for (Building b : buildings) {
            if (!b.contains(x, y)) {
                continue;
            }
            if (b.getType() != ZoneType.HOUSE
                    && b.getType() != ZoneType.CAFE
                    && b.getType() != ZoneType.LIBRARY) {
                break;
            }
            int doorY = b.getY() + b.getHeight() - 1;
            int bestX = b.getX() + b.getWidth() / 2;
            int bestDist = Integer.MAX_VALUE;
            for (int gx = b.getX(); gx < b.getX() + b.getWidth(); gx++) {
                if (!isWalkable(gx, doorY)) {
                    continue;
                }
                int d = Math.abs(gx - x);
                if (d < bestDist) {
                    bestDist = d;
                    bestX = gx;
                }
            }
            if (isWalkable(bestX, doorY)) {
                return new int[]{bestX, doorY};
            }
        }
        // 螺旋搜索最近可走格
        for (int r = 1; r < size; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (isWalkable(nx, ny)) {
                        return new int[]{nx, ny};
                    }
                }
            }
        }
        return new int[]{x, y};
    }

    public Set<Long> blockedKeys() {
        Set<Long> keys = new HashSet<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (blocked[y][x]) {
                    keys.add(pack(x, y));
                }
            }
        }
        return keys;
    }

    public static long pack(int x, int y) {
        return (((long) x) << 32) | (y & 0xffffffffL);
    }

    private static void markIfIn(boolean[][] blocked, int x, int y, int n) {
        if (inBounds(x, y, n)) {
            blocked[y][x] = true;
        }
    }

    private static boolean inBounds(int x, int y, int n) {
        return x >= 0 && y >= 0 && x < n && y < n;
    }
}
