package map;

import java.util.HashMap;
import java.util.Map;

public class GridMap {
    private final int width;
    private final int height;
    private final double cellSize;
    private final Map<String, Location> nodeLocations = new HashMap<>();
    private final boolean[][] walkable;

    public GridMap(int width, int height, double cellSize) {
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.walkable = new boolean[width][height];
    }

    public void setWalkable(int x, int y, boolean isWalkable) {
        if (isValid(x, y)) this.walkable[x][y] = isWalkable;
    }

    public boolean isWalkable(int x, int y) {
        return isValid(x, y) && walkable[x][y];
    }

    public void registerNodeLocation(String nodeId, Location loc) {
        nodeLocations.put(nodeId, loc);
    }

    public Location getNodeLocation(String nodeId) {
        return nodeLocations.get(nodeId);
    }

    private boolean isValid(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public double getCellSize() { return cellSize; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}