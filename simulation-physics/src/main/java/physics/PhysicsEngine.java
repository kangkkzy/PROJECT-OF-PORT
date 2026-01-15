package physics;

import Instruction.Instruction;
import map.GridMap;
import map.Location;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsEngine {
    private final GridMap gridMap;
    private final ConcurrentHashMap<Location, Set<String>> cellLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Location>> entityAllocations = new ConcurrentHashMap<>();

    public PhysicsEngine(GridMap gridMap) {
        this.gridMap = gridMap;
    }

    public void lockResources(String entityId, List<Location> locations) {
        if (locations == null || locations.isEmpty()) return;
        synchronized (this) {
            for (Location loc : locations) {
                cellLocks.computeIfAbsent(loc, k -> ConcurrentHashMap.newKeySet()).add(entityId);
            }
            entityAllocations.computeIfAbsent(entityId, k -> new HashSet<>()).addAll(locations);
        }
    }

    public void unlockSingleResource(String entityId, Location loc) {
        synchronized (this) {
            Set<String> occupiers = cellLocks.get(loc);
            if (occupiers != null) {
                occupiers.remove(entityId);
                if (occupiers.isEmpty()) cellLocks.remove(loc);
            }
            entityAllocations.getOrDefault(entityId, Collections.emptySet()).remove(loc);
        }
    }

    public boolean detectCollision(Location targetLoc, String selfId) {
        return detectCollision(targetLoc, selfId, null, null);
    }

    public boolean detectCollision(Location targetLoc, String selfId, Instruction inst, Location goal) {
        if (!gridMap.isWalkable(targetLoc.x(), targetLoc.y())) return true;

        Set<String> occupiers = cellLocks.get(targetLoc);
        if (occupiers == null || occupiers.isEmpty()) return false;
        if (occupiers.size() == 1 && occupiers.contains(selfId)) return false;

        // 协同豁免
        if (inst != null && targetLoc.equals(goal)) {
            String locType = gridMap.getLocationType(goal);
            String targetCraneId = "QUAY".equals(locType) ? inst.getTargetQC() : inst.getTargetYC();

            // 如果该位置只有我的合作伙伴，允许进入
            if (targetCraneId != null && occupiers.size() == 1 && occupiers.contains(targetCraneId)) return false;
            // 如果只有我和合作伙伴，也允许
            if (targetCraneId != null && occupiers.size() == 2 && occupiers.contains(targetCraneId) && occupiers.contains(selfId)) return false;
        }

        return occupiers.stream().anyMatch(occ -> !occ.equals(selfId));
    }

    public String getOccupier(Location loc) {
        Set<String> occupiers = cellLocks.get(loc);
        return (occupiers != null && !occupiers.isEmpty()) ? occupiers.iterator().next() : null;
    }
}