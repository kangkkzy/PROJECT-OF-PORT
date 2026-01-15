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
    private String targetQC;
    private String targetYC;
    private String targetIT;
    private int priority;
    private String status;
    private Instant generateTime;

    // 性能分析核心字段
    private long startTime;    // 任务开始执行的时间戳
    private long endTime;      // 任务彻底完成的时间戳
    private long expectedDuration = 0;
    private Map<String, Object> extraParameters = new HashMap<>();

    public Instruction() {}

    public Instruction(String instructionId, InstructionType type, String origin, String destination) {
        this.instructionId = instructionId;
        this.type = type;
        this.origin = origin;
        this.destination = destination;
        this.status = "PENDING";
        this.generateTime = Instant.now();
    }

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
    public void setContainerWeight(double containerWeight) { this.containerWeight = containerWeight; }
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
    public void setGenerateTime(Instant generateTime) { this.generateTime = generateTime; }
    public void setGenerateTime(long epochMillis) { this.generateTime = Instant.ofEpochMilli(epochMillis); }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public long getExpectedDuration() { return expectedDuration; }
    public void setExpectedDuration(long expectedDuration) { this.expectedDuration = expectedDuration; }

    public void assignToIT(String itId) {
        this.targetIT = itId;
        this.status = "ASSIGNED";
    }

    public void markInProgress(long now) {
        this.status = "IN_PROGRESS";
        this.startTime = now;
    }

    public void markCompleted(long now) {
        this.status = "COMPLETED";
        this.endTime = now;
    }
}