package Instruction;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Instruction {
    // 关键修复：映射 JSON 中的 "id" 到 instructionId
    @JsonProperty("id")
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
    private String status = "PENDING";
    private Instant generateTime;

    private long startTime;
    private long endTime;
    private long expectedDuration = 0;
    private Map<String, Object> extraParameters = new HashMap<>();

    public Instruction() {}

    public Instruction(String instructionId, InstructionType type, String origin, String destination) {
        this.instructionId = instructionId;
        this.type = type;
        this.origin = origin;
        this.destination = destination;
        this.generateTime = Instant.now();
    }

    public String getInstructionId() { return instructionId; }
    public void setInstructionId(String id) { this.instructionId = id; }

    public InstructionType getType() { return type; }
    public void setType(InstructionType type) { this.type = type; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String dest) { this.destination = dest; }

    public double getContainerWeight() { return containerWeight; }
    public void setContainerWeight(double weight) { this.containerWeight = weight; }

    public String getTargetQC() { return targetQC; }
    public void setTargetQC(String id) { this.targetQC = id; }

    public String getTargetYC() { return targetYC; }
    public void setTargetYC(String id) { this.targetYC = id; }

    public String getTargetIT() { return targetIT; }
    public void setTargetIT(String id) { this.targetIT = id; }

    public int getPriority() { return priority; }
    public void setPriority(int p) { this.priority = p; }

    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }

    public Instant getGenerateTime() { return generateTime; }
    public void setGenerateTime(Instant time) { this.generateTime = time; }
    // 兼容 JSON long 时间戳
    public void setGenerateTime(long epochMillis) { this.generateTime = Instant.ofEpochMilli(epochMillis); }

    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }

    public long getExpectedDuration() { return expectedDuration; }
    public void setExpectedDuration(long d) { this.expectedDuration = d; }

    public void markInProgress(long now) {
        this.status = "IN_PROGRESS";
        this.startTime = now;
    }

    public void markCompleted(long now) {
        this.status = "COMPLETED";
        this.endTime = now;
    }
}