package Instruction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Instruction {
    // 必填字段直接映射
    @JsonProperty("id") private String instructionId;
    @JsonProperty("type") private InstructionType type;
    private String origin;
    private String destination;

    // 业务字段
    private String containerId;
    private double containerWeight;
    private int priority = 1;
    private long expectedDuration;

    // 目标设备指派
    private String targetQC;
    private String targetYC;
    private String targetIT;

    // 自动解包 parameters 到这个 Map
    @JsonProperty("parameters")
    private final Map<String, Object> extraParameters = new HashMap<>();

    // 运行时状态
    private String status = "PENDING";
    private Instant generateTime = Instant.now();

    // 空构造供 Jackson 使用
    public Instruction() {}

    public Instruction(String id, InstructionType type, String origin, String destination) {
        this.instructionId = id;
        this.type = type;
        this.origin = origin;
        this.destination = destination;
    }

    // Setters for JSON loading
    public void setGenerateTime(long epochMillis) { this.generateTime = Instant.ofEpochMilli(epochMillis); }

    // Getters & Setters
    public String getInstructionId() { return instructionId; }
    public InstructionType getType() { return type; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public String getTargetQC() { return targetQC; }
    public String getTargetYC() { return targetYC; }
    public String getTargetIT() { return targetIT; }
    public double getContainerWeight() { return containerWeight; }
    public String getContainerId() { return containerId; }
    public int getPriority() { return priority; }
    public long getExpectedDuration() { return expectedDuration; }
    public Instant getGenerateTime() { return generateTime; }

    public void setStatus(String status) { this.status = status; }
    public void markInProgress() { this.status = "IN_PROGRESS"; }
    public void markCompleted() { this.status = "COMPLETED"; }

    // 简单的任务指派逻辑
    public void assignToIT(String itId) {
        this.targetIT = itId;
        this.status = "ASSIGNED";
    }
}
