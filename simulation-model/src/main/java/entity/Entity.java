package entity;

import map.Location; // 关键：导入新类
import java.util.LinkedList;
import java.util.List;

public abstract class Entity {
    protected String id;
    protected EntityType type;

    // 修改点1：新增 Location 类型的当前位置
    protected Location currentLocation;
    // 修改点2：新增初始节点ID字段
    protected String initialNodeId;

    protected String currentInstructionId;
    protected EntityStatus status;
    protected long lastUpdateTime;

    // 修改点3：路径列表改为 Location 类型
    protected List<Location> remainingPath;

    public Entity(String id, EntityType type, String initialPosition) {
        this.id = id;
        this.type = type;
        this.initialNodeId = initialPosition; // 保存初始ID
        this.currentLocation = null;
        this.currentInstructionId = null;
        this.status = EntityStatus.IDLE;
        this.lastUpdateTime = 0;
        this.remainingPath = new LinkedList<>();
    }

    // Getters and Setters
    public String getId() { return id; }
    public EntityType getType() { return type; }

    // 新增方法：SimpleScheduler 需要调用这个
    public Location getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Location location) { this.currentLocation = location; }

    // 新增方法：SimpleScheduler 需要调用这个
    public String getInitialNodeId() { return initialNodeId; }

    // 兼容旧方法：返回 String 类型的坐标
    public String getCurrentPosition() {
        return currentLocation != null ? currentLocation.toKey() : null;
    }

    // 兼容旧方法：允许设置 String 类型的坐标 (解析为 Location)
    public void setCurrentPosition(String posKey) {
        if (posKey != null) {
            this.currentLocation = Location.parse(posKey);
        }
    }

    public String getCurrentInstructionId() { return currentInstructionId; }
    public void setCurrentInstructionId(String instructionId) { this.currentInstructionId = instructionId; }

    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) {
        this.status = status;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public boolean isAvailable() {
        return status == EntityStatus.IDLE || status == EntityStatus.WAITING;
    }

    // 修改点4：路径操作全部改为 Location
    public void setRemainingPath(List<Location> path) {
        this.remainingPath = (path != null) ? new LinkedList<>(path) : new LinkedList<>();
    }

    public List<Location> getRemainingPath() { return remainingPath; }

    public boolean hasRemainingPath() {
        return remainingPath != null && !remainingPath.isEmpty();
    }

    public Location popNextStep() {
        if (hasRemainingPath()) {
            return remainingPath.remove(0);
        }
        return null;
    }

    public abstract double getMaxSpeed();
    public abstract double getAcceleration();
    public abstract double getDeceleration();
}