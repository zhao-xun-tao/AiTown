package com.aitown.engine;

import com.aitown.model.Building;
import com.aitown.model.GridPoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 四向网格 A* 寻路（后端粗路径）。
 */
@Service
public class PathfindingService {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public List<GridPoint> findPath(int sx, int sy, int gx, int gy, List<Building> buildings) {
        CollisionMap map = CollisionMap.fromBuildings(buildings);
        int[] start = map.snapToWalkable(sx, sy, buildings);
        int[] goal = map.snapToWalkable(gx, gy, buildings);
        sx = start[0];
        sy = start[1];
        gx = goal[0];
        gy = goal[1];

        if (sx == gx && sy == gy) {
            return List.of(new GridPoint(gx, gy));
        }
        if (!map.isWalkable(gx, gy)) {
            return List.of();
        }

        int n = map.size();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(a -> a.f));
        Map<Long, Integer> gScore = new HashMap<>();
        Map<Long, Long> came = new HashMap<>();

        long startKey = CollisionMap.pack(sx, sy);
        gScore.put(startKey, 0);
        open.add(new Node(sx, sy, 0, heuristic(sx, sy, gx, gy)));

        while (!open.isEmpty()) {
            Node cur = open.poll();
            if (cur.x == gx && cur.y == gy) {
                return reconstruct(came, gx, gy);
            }
            long ck = CollisionMap.pack(cur.x, cur.y);
            int cg = gScore.getOrDefault(ck, Integer.MAX_VALUE);
            if (cur.g > cg) {
                continue;
            }

            for (int[] d : DIRS) {
                int nx = cur.x + d[0];
                int ny = cur.y + d[1];
                if (!map.isWalkable(nx, ny)) {
                    continue;
                }
                long nk = CollisionMap.pack(nx, ny);
                int ng = cg + 1;
                if (ng >= gScore.getOrDefault(nk, Integer.MAX_VALUE)) {
                    continue;
                }
                came.put(nk, ck);
                gScore.put(nk, ng);
                open.add(new Node(nx, ny, ng, ng + heuristic(nx, ny, gx, gy)));
            }
        }
        return List.of();
    }

    private static int heuristic(int x, int y, int gx, int gy) {
        return Math.abs(x - gx) + Math.abs(y - gy);
    }

    private static List<GridPoint> reconstruct(Map<Long, Long> came, int gx, int gy) {
        List<GridPoint> path = new ArrayList<>();
        long cur = CollisionMap.pack(gx, gy);
        path.add(new GridPoint(gx, gy));
        while (came.containsKey(cur)) {
            cur = came.get(cur);
            int x = (int) (cur >> 32);
            int y = (int) cur;
            path.add(new GridPoint(x, y));
        }
        Collections.reverse(path);
        // 去掉起点（当前格），只保留后续路点
        if (path.size() > 1) {
            path.remove(0);
        }
        return path;
    }

    private record Node(int x, int y, int g, int f) {
    }
}
