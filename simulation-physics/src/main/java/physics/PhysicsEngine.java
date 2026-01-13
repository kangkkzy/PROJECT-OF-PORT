package physics;

import entity.Entity;
import map.Segment;
import java.util.*;
// 检测物理碰撞是否发生
public class PhysicsEngine {

    private List<Entity> allEntities;

    public PhysicsEngine() {
        this.allEntities = new ArrayList<>();
    }

    public void registerEntity(Entity entity) {
        this.allEntities.add(entity);
    }
// 碰撞发生返回 true 反之
    public boolean detectCollision(String segmentId, String selfEntityId) {
        return false;
    }
}