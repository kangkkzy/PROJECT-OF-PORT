package map;

import java.util.HashMap;
import java.util.Map;

public class GridMap {
    private final int width;
    private final int height;
    private final double cellSize;

    // 坐标编码 -> 占用者ID (null表示无占用)
    private final Map<String, String> occupiedCells;

    // 坐标编码 -> 是否可行走
    private final boolean[][] walkable;

    // 节点ID映射表：将 JSON 中的 "QUAY_01" 映射为 "10_20" 格式的坐标字符串
    private final Map<String, String> nodeLocationMap;

    public GridMap(int width, int height, double cellSize) {
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.occupiedCells = new HashMap<>();
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

    public void registerNodeLocation(String nodeId, int x, int y) {
        nodeLocationMap.put(nodeId, toKey(x, y));
    }

    public String getNodePosition(String nodeId) {
        return nodeLocationMap.get(nodeId);
    }

    // 坐标键生成工具：为了通用性，使用 "x_y" 字符串表示位置
    public static String toKey(int x, int y) {
        return x + "_" + y;
    }

    public static int[] parseKey(String key) {
        String[] parts = key.split("_");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    public boolean isValid(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public double getCellSize() {
        return cellSize;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
}