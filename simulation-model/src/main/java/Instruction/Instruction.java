package Instruction;

import java.time.Instant;

public class Instruction {
    private String instructionId;
    private InstructionType type;
    private String origin;       // 起始位置
    private String destination;  // 目标位置
    private String containerId;  // 集装箱ID
    private double containerWeight; // 集装箱重量
    private String targetQC;     // 目标桥吊（针对岸边作业）
    private String targetYC;     // 目标龙门吊（针对堆场作业）
    private String targetIT;     // 分配给的集卡
    private int priority;        // 优先级
    private String status;       // 状态：PENDING, ASSIGNED, IN_PROGRESS, COMPLETED
    private Instant generateTime; // 新增：生成时间

    public Instruction(String instructionId, InstructionType type, String origin, String destination) {
        this.instructionId = instructionId;
        this.type = type;
        this.origin = origin;
        this.destination = destination;
        this.priority = 1;
        this.status = "PENDING";
        this.generateTime = Instant.now();
    }

    // Getters and Setters
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

    // 新增的时间 Getter/Setter
    public Instant getGenerateTime() { return generateTime; }
    public void setGenerateTime(Instant generateTime) { this.generateTime = generateTime; }

    public boolean isAssigned() { return "ASSIGNED".equals(status) || "IN_PROGRESS".equals(status); }
    public boolean isCompleted() { return "COMPLETED".equals(status); }

    public void assignToIT(String itId) {
        this.targetIT = itId;
        this.status = "ASSIGNED";
    }

    public void markInProgress() { this.status = "IN_PROGRESS"; }
    public void markCompleted() { this.status = "COMPLETED"; }

    @Override
    public String toString() {
        return String.format("指令[%s] 类型:%s 从%s到%s 状态:%s",
                instructionId, type.getChineseName(), origin, destination, status);
    }
}
