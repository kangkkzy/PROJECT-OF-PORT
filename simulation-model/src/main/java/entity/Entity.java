package entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    // 关键修复：显式映射 JSON 字段
    @JsonProperty("id")
    protected String id;

    @JsonProperty("type")
    protected EntityType type;

    @JsonProperty("initialPosition")
    protected String initialPosition;

    // 运行时状态 (不序列化)
    protected Location currentLocation;
    protected String currentInstructionId;
    protected EntityStatus status = EntityStatus.IDLE;
    protected List<Location> remainingPath = new LinkedList<>();

    public Entity() {}

    public Entity(String id, EntityType type, String initialPosition) {
        this.id = id;
        this.type = type;
        this.initialPosition = initialPosition;
    }

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

    public abstract double getMaxSpeed();
    public abstract double getAcceleration();
    public abstract double getDeceleration();
}