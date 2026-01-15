package map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GridMap {
    private final int width;
    private final int height;
    private final double cellSize;

    private final Map<String, Location> idToLoc = new HashMap<>();
    private final Map<Location, String> locToId = new HashMap<>();
    private final Map<Location, String> locToType = new HashMap<>();
    private final Map<String, List<String>> typeToIds = new HashMap<>();

    private final boolean[][] walkable;

    public GridMap(int width, int height, double cellSize) {
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.walkable = new boolean[width][height];
    }

    public void registerNode(String nodeId, String type, Location loc) {
        idToLoc.put(nodeId, loc);
        locToId.put(loc, nodeId);
        if (type != null) {
            locToType.put(loc, type);
            typeToIds.computeIfAbsent(type, k -> new ArrayList<>()).add(nodeId);
        }
    }

    public Location getNodeLocation(String nodeId) { return idToLoc.get(nodeId); }
    public String getNodeId(Location loc) { return locToId.get(loc); }
    public String getLocationType(Location loc) { return locToType.getOrDefault(loc, "UNKNOWN"); }
    public List<String> getNodesByType(String type) { return typeToIds.getOrDefault(type, Collections.emptyList()); }

    public void setWalkable(int x, int y, boolean isWalkable) {
        if (isValid(x, y)) this.walkable[x][y] = isWalkable;
    }

    public boolean isWalkable(int x, int y) { return isValid(x, y) && walkable[x][y]; }
    private boolean isValid(int x, int y) { return x >= 0 && x < width && y >= 0 && y < height; }
    public double getCellSize() { return cellSize; }
}