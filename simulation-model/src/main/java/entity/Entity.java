package entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import map.Location;
import java.util.LinkedList;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = QC.class, name = "QC"),
        @JsonSubTypes.Type(value = YC.class, name = "YC"),
        @JsonSubTypes.Type(value = IT.class, name = "IT")
})
public abstract class Entity {
    // 基础字段直接映射 JSON
    protected String id;
    protected EntityType type;
    protected String initialPosition;

    // 运行时状态 (不序列化)
    protected Location currentLocation;
    protected String currentInstructionId;
    protected EntityStatus status = EntityStatus.IDLE;
    protected List<Location> remainingPath = new LinkedList<>();

    // 空构造器供 Jackson 使用
    public Entity() {}

    public Entity(String id, EntityType type, String initialPosition) {
        this.id = id;
        this.type = type;
        this.initialPosition = initialPosition;
    }

    // --- 简化后的 Getters/Setters ---
    public String getId() { return id; }
    public EntityType getType() { return type; }
    public String getInitialNodeId() { return initialPosition; }

    public Location getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Location location) { this.currentLocation = location; }

    public String getCurrentInstructionId() { return currentInstructionId; }
    public void setCurrentInstructionId(String id) { this.currentInstructionId = id; }

    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }

    public void setRemainingPath(List<Location> path) {
        this.remainingPath = (path != null) ? new LinkedList<>(path) : new LinkedList<>();
    }
    public List<Location> getRemainingPath() { return remainingPath; }
    public boolean hasRemainingPath() { return remainingPath != null && !remainingPath.isEmpty(); }

    public Location popNextStep() {
        return hasRemainingPath() ? remainingPath.remove(0) : null;
    }

    // 兼容旧代码
    public String getCurrentPosition() {
        return currentLocation != null ? currentLocation.toKey() : null;
    }

    // 抽象物理属性
    public abstract double getMaxSpeed();
    public abstract double getAcceleration();
    public abstract double getDeceleration();
}