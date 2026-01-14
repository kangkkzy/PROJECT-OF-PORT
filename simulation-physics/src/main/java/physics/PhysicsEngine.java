package physics;

import map.GridMap;
import map.Location;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsEngine {
    private final GridMap gridMap;
    // Location 是 Record，自动实现了正确的 equals/hashCode，可用作 Key
    private final Map<Location, String> cellLocks = new ConcurrentHashMap<>();
    private final Map<String, List<Location>> entityAllocations = new ConcurrentHashMap<>();

    public PhysicsEngine(GridMap gridMap) { this.gridMap = gridMap; }

    public boolean detectCollision(Location targetLoc, String selfId) {
        String occupier = cellLocks.get(targetLoc);
        return occupier != null && !occupier.equals(selfId);
    }

    public String getOccupier(Location loc) { return cellLocks.get(loc); }

    public synchronized void lockResources(String entityId, List<Location> locations) {
        List<Location> allocated = entityAllocations.computeIfAbsent(entityId, k -> new ArrayList<>());
        for (Location loc : locations) {
            cellLocks.put(loc, entityId);
            allocated.add(loc);
        }
    }

    public synchronized void unlockSingleResource(String entityId, Location loc) {
        if (loc != null && entityId.equals(cellLocks.get(loc))) {
            cellLocks.remove(loc);
            List<Location> locs = entityAllocations.get(entityId);
            if (locs != null) locs.remove(loc);
        }
    }
}