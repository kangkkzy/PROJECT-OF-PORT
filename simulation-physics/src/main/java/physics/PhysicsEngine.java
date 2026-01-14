package physics;

import entity.Entity;
import map.GridMap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsEngine {
    private final GridMap gridMap;
    // 记录占用： "x_y" -> entityId
    private final Map<String, String> cellLocks;
    // 反向索引： entityId -> List<"x_y">
    private final Map<String, List<String>> entityAllocations;

    public PhysicsEngine(GridMap gridMap) {
        this.gridMap = gridMap;
        this.cellLocks = new ConcurrentHashMap<>();
        this.entityAllocations = new ConcurrentHashMap<>();
    }

    public void registerEntity(Entity entity) {
        // 在 Grid 模式下，注册时不需要额外动作，位置动态更新
    }

    // 检测目标坐标字符串 (格式 "x_y") 是否被其他人占用
    public boolean detectCollision(String targetPosKey, String selfEntityId) {
        String occupier = cellLocks.get(targetPosKey);
        // 如果被占用，且占用者不是自己，则冲突
        return occupier != null && !occupier.equals(selfEntityId);
    }

    // 锁定坐标资源
    public synchronized void lockResources(String entityId, List<String> posKeys) {
        List<String> allocated = entityAllocations.computeIfAbsent(entityId, k -> new ArrayList<>());
        for (String key : posKeys) {
            cellLocks.put(key, entityId);
            allocated.add(key);
        }
    }

    // 释放某实体持有的所有资源
    public synchronized void unlockResources(String entityId) {
        List<String> keys = entityAllocations.remove(entityId);
        if (keys != null) {
            for (String key : keys) {
                // 双重检查，防止误删
                if (entityId.equals(cellLocks.get(key))) {
                    cellLocks.remove(key);
                }
            }
        }
    }

    // 释放单个位置 (用于移动时的步进释放)
    public synchronized void unlockSingleResource(String entityId, String posKey) {
        if (entityId.equals(cellLocks.get(posKey))) {
            cellLocks.remove(posKey);
            // 也要从 entityAllocations 移除
            List<String> keys = entityAllocations.get(entityId);
            if (keys != null) {
                keys.remove(posKey);
            }
        }
    }
}