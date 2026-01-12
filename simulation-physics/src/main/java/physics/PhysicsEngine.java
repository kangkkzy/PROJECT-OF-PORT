package physics; // 必须确保留在这个包下

import java.util.*;

/**
 * 物理引擎 (修正版)
 * 职责：仅负责冲突检测和路段占用管理
 */
public class PhysicsEngine {
    // 记录路段占用情况: SegmentId -> EntityId
    private final Map<String, String> occupiedSegments;

    public PhysicsEngine() { // 之前这里可能依赖 PortMap，现在不需要了
        this.occupiedSegments = new HashMap<>();
    }

    /**
     * 尝试占用路径上的所有路段
     * @param segmentIds 路径经过的路段ID列表
     * @param entityId 申请占用的实体ID
     * @throws RuntimeException 如果发生碰撞（路段已被占用）
     */
    public void checkAndOccupyPath(List<String> segmentIds, String entityId) {
        synchronized (occupiedSegments) {
            // 1. 碰撞检测
            for (String segId : segmentIds) {
                if (occupiedSegments.containsKey(segId)) {
                    String occupier = occupiedSegments.get(segId);
                    // 如果被别人占用 (且不是自己)
                    if (!occupier.equals(entityId)) {
                        throw new RuntimeException(String.format(
                                "物理冲突: 路段[%s] 已被实体[%s] 占用，实体[%s] 无法进入",
                                segId, occupier, entityId
                        ));
                    }
                }
            }

            // 2. 占用路段 (如果检测通过)
            for (String segId : segmentIds) {
                occupiedSegments.put(segId, entityId);
            }
        }
    }

    /**
     * 释放实体占用的所有路段
     */
    public void releaseAllByEntity(String entityId) {
        synchronized (occupiedSegments) {
            // 删除所有 value 等于 entityId 的条目
            occupiedSegments.values().removeIf(val -> val.equals(entityId));
        }
    }

    // 保留旧方法以兼容其他代码（可选）
    public boolean isSegmentOccupied(String segmentId) {
        return occupiedSegments.containsKey(segmentId);
    }
}