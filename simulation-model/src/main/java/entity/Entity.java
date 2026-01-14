package entity;

import entity.EntityStatus;
import entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public abstract class Entity {
    protected String id;
    protected EntityType type;
    protected String currentPosition;
    protected String currentInstructionId;
    protected EntityStatus status;
    protected long lastUpdateTime;

    // 存储剩余的路径段
    protected List<String> remainingPath;

    public Entity(String id, EntityType type, String initialPosition) {
        this.id = id;
        this.type = type;
        this.currentPosition = initialPosition;
        this.currentInstructionId = null;
        this.status = EntityStatus.IDLE;
        this.lastUpdateTime = 0;
        this.remainingPath = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public EntityType getType() {
        return type;
    }

    public String getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(String position) {
        this.currentPosition = position;
    }

    public String getCurrentInstructionId() {
        return currentInstructionId;
    }

    public void setCurrentInstructionId(String instructionId) {
        this.currentInstructionId = instructionId;
    }

    public EntityStatus getStatus() {
        return status;
    }

    public void setStatus(EntityStatus status) {
        this.status = status;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void updateLastUpdateTime() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public boolean isAvailable() {
        return status == EntityStatus.IDLE || status == EntityStatus.WAITING;
    }

    // 路径管理方法
    public void setRemainingPath(List<String> path) {
        this.remainingPath = (path != null) ? new ArrayList<>(path) : new ArrayList<>();
    }

    public List<String> getRemainingPath() {
        return remainingPath;
    }

    public boolean hasRemainingPath() {
        return remainingPath != null && !remainingPath.isEmpty();
    }

    public String popNextSegment() {
        if (hasRemainingPath()) {
            return remainingPath.remove(0);
        }
        return null;
    }

    public abstract double getMaxSpeed();
    public abstract double getAcceleration();
    public abstract double getDeceleration();

    @Override
    public String toString() {
        return String.format("%s[%s] 位置:%s 状态:%s 指令:%s",
                type.getChineseName(), id, currentPosition, status.getChineseName(),
                currentInstructionId != null ? currentInstructionId : "无");
    }
}