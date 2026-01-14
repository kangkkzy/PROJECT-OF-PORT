package plugins;

import decision.RoutePlanner;
import map.GridMap;
import java.util.*;

// 实现 RoutePlanner 接口，返回 List<String>，内容为 "x_y" 格式的坐标
public class GridRoutePlanner implements RoutePlanner {
    private final GridMap gridMap;
    private static final int[][] DIRS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}; // 上下左右

    public GridRoutePlanner(GridMap gridMap) {
        this.gridMap = gridMap;
    }

    @Override
    public List<String> searchRoute(String originId, String destinationId) {
        String startKey = gridMap.getNodePosition(originId);
        String endKey = gridMap.getNodePosition(destinationId);

        if (startKey == null || endKey == null || startKey.equals(endKey)) {
            return Collections.emptyList();
        }

        int[] start = GridMap.parseKey(startKey);
        int[] end = GridMap.parseKey(endKey);

        return aStarSearch(start[0], start[1], end[0], end[1]);
    }

    private List<String> aStarSearch(int startX, int startY, int endX, int endY) {
        PriorityQueue<NodeRecord> openList = new PriorityQueue<>(Comparator.comparingDouble(n -> n.cost + n.heuristic));
        Map<String, Double> costSoFar = new HashMap<>();
        Map<String, String> cameFrom = new HashMap<>();

        String startKey = GridMap.toKey(startX, startY);
        String endKey = GridMap.toKey(endX, endY);

        openList.add(new NodeRecord(startX, startY, 0, heuristic(startX, startY, endX, endY)));
        costSoFar.put(startKey, 0.0);
        cameFrom.put(startKey, null);

        boolean found = false;

        while (!openList.isEmpty()) {
            NodeRecord current = openList.poll();
            String currentKey = GridMap.toKey(current.x, current.y);

            if (currentKey.equals(endKey)) {
                found = true;
                break;
            }

            for (int[] dir : DIRS) {
                int nextX = current.x + dir[0];
                int nextY = current.y + dir[1];
                String nextKey = GridMap.toKey(nextX, nextY);

                if (!gridMap.isWalkable(nextX, nextY)) continue;

                double newCost = costSoFar.get(currentKey) + 1.0; // 假设每格代价为1

                if (!costSoFar.containsKey(nextKey) || newCost < costSoFar.get(nextKey)) {
                    costSoFar.put(nextKey, newCost);
                    double h = heuristic(nextX, nextY, endX, endY);
                    openList.add(new NodeRecord(nextX, nextY, newCost, h));
                    cameFrom.put(nextKey, currentKey);
                }
            }
        }

        if (!found) return Collections.emptyList();

        // 重建路径
        LinkedList<String> path = new LinkedList<>();
        String curr = endKey;
        while (curr != null && !curr.equals(startKey)) {
            path.addFirst(curr);
            curr = cameFrom.get(curr);
        }
        return path;
    }

    private double heuristic(int x1, int y1, int x2, int y2) {
        // 曼哈顿距离
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private static class NodeRecord {
        int x, y;
        double cost;
        double heuristic;

        NodeRecord(int x, int y, double cost, double heuristic) {
            this.x = x; this.y = y; this.cost = cost; this.heuristic = heuristic;
        }
    }
}