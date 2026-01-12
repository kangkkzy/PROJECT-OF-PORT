import entity.Entity;
import map.PortMap;
import java.util.*;

public class PhysicsEngine {
    private PortMap portMap;
    private Map<String, Entity> entities;
    private Map<String, String> occupiedSegments; // 路段占用情况

    public PhysicsEngine(PortMap portMap) {
        this.portMap = portMap;
        this.entities = new HashMap<>();
        this.occupiedSegments = new HashMap<>();
    }

    public void registerEntity(Entity entity) {
        entities.put(entity.getId(), entity);
    }

    public boolean canMoveTo(String entityId, String targetPosition) {
        Entity entity = entities.get(entityId);
        if (entity == null) {
            return false;
        }

        String currentPosition = entity.getCurrentPosition();
        if (currentPosition.equals(targetPosition)) {
            return true;
        }

        // 查找路径
        List<String> path = portMap.findPath(currentPosition, targetPosition);
        if (path.isEmpty()) {
            return false;
        }

        // 检查路径上的每个路段是否可用
        for (int i = 0; i < path.size() - 1; i++) {
            String fromNode = path.get(i);
            String toNode = path.get(i + 1);

            // 获取路段
            map.Segment segment = portMap.getSegmentBetween(fromNode, toNode);
            if (segment == null) {
                return false;
            }

            // 检查路段是否被占用
            String occupier = occupiedSegments.get(segment.getId());
            if (occupier != null && !occupier.equals(entityId)) {
                return false;
            }
        }

        return true;
    }

    public boolean tryOccupySegment(String segmentId, String entityId) {
        synchronized (occupiedSegments) {
            if (occupiedSegments.containsKey(segmentId)) {
                return false;
            }
            occupiedSegments.put(segmentId, entityId);
            return true;
        }
    }

    public void releaseSegment(String segmentId) {
        synchronized (occupiedSegments) {
            occupiedSegments.remove(segmentId);
        }
    }

    public void releaseAllSegments(String entityId) {
        synchronized (occupiedSegments) {
            occupiedSegments.entrySet().removeIf(entry -> entry.getValue().equals(entityId));
        }
    }

    public boolean isSegmentOccupied(String segmentId) {
        return occupiedSegments.containsKey(segmentId);
    }

    public String getSegmentOccupier(String segmentId) {
        return occupiedSegments.get(segmentId);
    }

    public List<String> getPath(String fromPosition, String toPosition) {
        return portMap.findPath(fromPosition, toPosition);
    }

    public double getPathDistance(List<String> path) {
        return portMap.calculatePathDistance(path);
    }

    public void updateEntityPosition(String entityId, String newPosition) {
        Entity entity = entities.get(entityId);
        if (entity != null) {
            entity.setCurrentPosition(newPosition);
        }
    }

    public Map<String, String> getOccupiedSegments() {
        return new HashMap<>(occupiedSegments);
    }

    public void clearAllOccupancies() {
        occupiedSegments.clear();
    }
}