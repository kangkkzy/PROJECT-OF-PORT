package physics;

import entity.Entity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 检测物理碰撞是否发生 并管理路段资源的锁定状态
public class PhysicsEngine {

    private List<Entity> allEntities;
// 路段被实体占用
    private final Map<String, String> segmentLocks;

    // 记录每个实体当前占用了哪些路段 方便快速释放
    private final Map<String, List<String>> entityAllocations;

    public PhysicsEngine() {
        this.allEntities = new ArrayList<>();
        this.segmentLocks = new ConcurrentHashMap<>();
        this.entityAllocations = new ConcurrentHashMap<>();
    }

    public void registerEntity(Entity entity) {
        this.allEntities.add(entity);
    }

    // 检测是否发生了碰撞
    public boolean detectCollision(String segmentId, String selfEntityId) {
        if (segmentId == null) return false;

        String occupierId = segmentLocks.get(segmentId);

        // 如果该路段没有被占用 或者占用者就是自己 则视为无冲突
        if (occupierId == null || occupierId.equals(selfEntityId)) {
            return false;
        }
        return true;
    }

    // 锁定路段资源
    public synchronized void lockSegments(String entityId, List<String> segmentIds) {
        if (segmentIds == null || segmentIds.isEmpty()) return;

        List<String> allocated = entityAllocations.computeIfAbsent(entityId, k -> new ArrayList<>());

        for (String segId : segmentIds) {
            // 记录占用状态
            segmentLocks.put(segId, entityId);
            // 记录到实体的名下
            allocated.add(segId);
        }
        // System.out.println("[DEBUG] 实体 " + entityId + " 锁定路段: " + segmentIds);
    }

    // 释放资源
    public synchronized void unlockResources(String entityId) {
        List<String> segments = entityAllocations.remove(entityId);

        if (segments != null) {
            for (String segId : segments) {
                // 只有当占用者确实是该实体时才释放（防止逻辑错误解开了别人的锁）
                if (entityId.equals(segmentLocks.get(segId))) {
                    segmentLocks.remove(segId);
                }
            }
        }
    }
}