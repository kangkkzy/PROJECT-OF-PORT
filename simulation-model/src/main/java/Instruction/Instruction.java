package Instruction;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Instruction {
    private String instructionId;
    private InstructionType type;
    private String origin;
    private String destination;

    private String containerId;
    private double containerWeight;

    // 目标设备
    private String targetQC;
    private String targetYC;
    private String targetIT;

    private int priority;
    private String status;
    private Instant generateTime;

    // 预期执行耗时
    private long expectedDuration = 0;

    // 扩展参数
    private Map<String, Object> extraParameters = new HashMap<>();

    public Instruction() {} // 空构造函数

    public Instruction(String instructionId, InstructionType type, String origin, String destination) {
        this.instructionId = instructionId;
        this.type = type;
        this.origin = origin;
        this.destination = destination;
        this.priority = 1;
        this.status = "PENDING";
        this.generateTime = Instant.now();
    }

    // --- Getters & Setters (修复报错的关键) ---

    public String getInstructionId() { return instructionId; }
    public void setInstructionId(String instructionId) { this.instructionId = instructionId; }

    public InstructionType getType() { return type; }
    public void setType(InstructionType type) { this.type = type; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public double getContainerWeight() { return containerWeight; }
    public void setContainerWeight(double weight) { this.containerWeight = weight; }

    // [修复] 添加缺失的 setter
    public String getTargetQC() { return targetQC; }
    public void setTargetQC(String targetQC) { this.targetQC = targetQC; }

    public String getTargetYC() { return targetYC; }
    public void setTargetYC(String targetYC) { this.targetYC = targetYC; }

    public String getTargetIT() { return targetIT; }
    public void setTargetIT(String targetIT) { this.targetIT = targetIT; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getGenerateTime() { return generateTime; }
    // [修复] 确保参数类型是 Instant
    public void setGenerateTime(Instant generateTime) { this.generateTime = generateTime; }

    // 兼容旧代码或 JSON 加载的辅助 setter (可选)
    public void setGenerateTime(long epochMillis) { this.generateTime = Instant.ofEpochMilli(epochMillis); }

    public long getExpectedDuration() { return expectedDuration; }
    public void setExpectedDuration(long expectedDuration) { this.expectedDuration = expectedDuration; }

    public void setExtraParameter(String key, Object value) { this.extraParameters.put(key, value); }
    public Object getExtraParameter(String key) { return this.extraParameters.get(key); }

    // 业务方法
    public void assignToIT(String itId) {
        this.targetIT = itId;
        this.status = "ASSIGNED";
    }

    public void markInProgress() { this.status = "IN_PROGRESS"; }
    public void markCompleted() { this.status = "COMPLETED"; }

    @Override
    public String toString() {
        return String.format("指令[%s] 类型:%s 优先级:%d 耗时:%dms",
                instructionId, type != null ? type.getChineseName() : "未知", priority, expectedDuration);
    }
}
