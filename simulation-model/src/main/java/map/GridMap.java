package map;

import java.util.HashMap;
import java.util.Map;

public class GridMap {
    private final int width;
    private final int height;
    private final double cellSize;

    // 修改点1：使用 Location 作为 Value
    private final Map<String, Location> nodeLocationMap;
    private final boolean[][] walkable;

    public GridMap(int width, int height, double cellSize) {
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.walkable = new boolean[width][height];
        this.nodeLocationMap = new HashMap<>();
    }

    public void setWalkable(int x, int y, boolean isWalkable) {
        if (isValid(x, y)) {
            this.walkable[x][y] = isWalkable;
        }
    }

    public boolean isWalkable(int x, int y) {
        return isValid(x, y) && walkable[x][y];
    }

    // 新增：支持直接查 Location 的可行性
    public boolean isWalkable(Location loc) {
        return loc != null && isWalkable(loc.x, loc.y);
    }

    public void registerNodeLocation(String nodeId, int x, int y) {
        nodeLocationMap.put(nodeId, new Location(x, y));
    }

    // 修改点2：新增核心方法，返回 Location 对象
    public Location getNodeLocation(String nodeId) {
        return nodeLocationMap.get(nodeId);
    }

    // 兼容旧方法：返回 String
    public String getNodePosition(String nodeId) {
        Location loc = nodeLocationMap.get(nodeId);
        return loc != null ? loc.toKey() : null;
    }

    // 辅助工具：String 解析 (保留用于兼容旧代码)
    public static int[] parseKey(String key) {
        String[] parts = key.split("_");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    public static String toKey(int x, int y) {
        return x + "_" + y;
    }

    public boolean isValid(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public double getCellSize() { return cellSize; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}