package event;

public class SimEvent implements Comparable<SimEvent> {
    private long timestamp;      // 事件发生时间
    private EventType type;      // 事件类型
    private String entityId;     // 相关设备ID
    private String instructionId; // 相关指令ID
    private String targetPosition; // 目标位置（针对到达事件）
    private Object data;         // 其他数据

    public SimEvent(long timestamp, EventType type, String entityId) {
        this.timestamp = timestamp;
        this.type = type;
        this.entityId = entityId;
    }

    public SimEvent(long timestamp, EventType type, String entityId, String instructionId) {
        this(timestamp, type, entityId);
        this.instructionId = instructionId;
    }

    public SimEvent(long timestamp, EventType type, String entityId, String instructionId, String targetPosition) {
        this(timestamp, type, entityId, instructionId);
        this.targetPosition = targetPosition;
    }

    // Getters and Setters
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
    }

    public String getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(String targetPosition) {
        this.targetPosition = targetPosition;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public int compareTo(SimEvent other) {
        return Long.compare(this.timestamp, other.timestamp);
    }

    @Override
    public String toString() {
        return String.format("事件[时间:%d, 类型:%s, 设备:%s, 指令:%s, 目标:%s]",
                timestamp, type.getChineseName(), entityId,
                instructionId != null ? instructionId : "无",
                targetPosition != null ? targetPosition : "无");
    }
}
