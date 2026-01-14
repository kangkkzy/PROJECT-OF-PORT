package physics;

import entity.Entity;
import map.GridMap;
import map.Location;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsEngine {
    private final GridMap gridMap;

    // 核心修改：Key 类型改为 Location
    private final Map<Location, String> cellLocks;
    private final Map<String, List<Location>> entityAllocations;

    public PhysicsEngine(GridMap gridMap) {
        this.gridMap = gridMap;
        this.cellLocks = new ConcurrentHashMap<>();
        this.entityAllocations = new ConcurrentHashMap<>();
    }

    public void registerEntity(Entity entity) {
        // 不需要额外操作
    }

    public boolean detectCollision(Location targetLoc, String selfEntityId) {
        String occupier = cellLocks.get(targetLoc);
        return occupier != null && !occupier.equals(selfEntityId);
    }

    public String getOccupier(Location loc) {
        return cellLocks.get(loc);
    }

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
            if (locs != null) {
                locs.remove(loc);
            }
        }
    }
}