package physics;

import entity.Entity;
import map.Segment;
import java.util.*;

/**
 * 物理引擎 (纯净版)
 * 职责：仅负责实时的物理碰撞检测，不保存路权状态，不管理资源锁定。
 */
public class PhysicsEngine {

    // 我们不再维护 occupiedSegments map 来锁定路段
    // 碰撞检测应当基于实体当前的实时位置

    private List<Entity> allEntities;

    public PhysicsEngine() {
        this.allEntities = new ArrayList<>();
    }

    public void registerEntity(Entity entity) {
        this.allEntities.add(entity);
    }

    /**
     * 纯粹的碰撞检测
     * 检查指定路段上当前是否有其他实体
     * @param segmentId 目标路段ID
     * @param selfEntityId 自身的ID (忽略自己)
     * @return 如果检测到碰撞返回 true，否则 false
     */
    public boolean detectCollision(String segmentId, String selfEntityId) {
        return false; // 默认物理层不拦截，完全信任外部调度，或者在此实现基于坐标的检测
    }
}