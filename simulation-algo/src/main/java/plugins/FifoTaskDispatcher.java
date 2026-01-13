package plugins;

import decision.TaskDispatcher;
import entity.Entity;
import entity.QC;
import entity.YC;
import Instruction.Instruction;
import Instruction.InstructionType;
import java.util.*;

public class FifoTaskDispatcher implements TaskDispatcher {

    // 冲突避让等待时间 (这是策略参数，可以保留，或改为从配置读取)
    private static final long COLLISION_WAIT_MS = 5000L;

    private final Map<String, List<Instruction>> instructionQueues;
    private final Set<String> assignedInstructionIds;

    public FifoTaskDispatcher() {
        this.instructionQueues = new HashMap<>();
        this.instructionQueues.put("QC", new ArrayList<>());
        this.instructionQueues.put("YC", new ArrayList<>());
        this.instructionQueues.put("IT", new ArrayList<>());
        this.instructionQueues.put("UNKNOWN", new ArrayList<>());
        this.assignedInstructionIds = new HashSet<>();
    }

    @Override
    public void onNewTaskSubmitted(Instruction instruction) {
        String queueKey = "UNKNOWN";
        if (instruction.getTargetQC() != null) queueKey = "QC";
        else if (instruction.getTargetYC() != null) queueKey = "YC";
        else if (instruction.getTargetIT() != null) queueKey = "IT";

        // 注意：这里删除了之前设置默认5000ms的代码
        // 耗时将在分配给具体设备时计算

        List<Instruction> queue = instructionQueues.get(queueKey);
        if(queue != null) {
            queue.add(instruction);
            // 简单的优先级排序策略
            queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed());
            System.out.println("[Out算法] FIFO接收任务: " + instruction.getInstructionId());
        }
    }

    @Override
    public Instruction assignTask(Entity entity) {
        List<Instruction> queue = instructionQueues.get(entity.getType().name());
        if (queue == null || queue.isEmpty()) return null;

        for (Instruction instruction : queue) {
            if (assignedInstructionIds.contains(instruction.getInstructionId())) continue;

            if (isEntitySuitable(entity, instruction)) {
                assignedInstructionIds.add(instruction.getInstructionId());
                instruction.assignToIT(entity.getId());

                // 【核心修改】动态计算耗时，不再使用硬编码
                long duration = calculateDuration(entity, instruction);
                instruction.setExpectedDuration(duration);

                return instruction;
            }
        }
        return null;
    }

    /**
     * 根据设备性能模型计算任务耗时
     */
    private long calculateDuration(Entity entity, Instruction instruction) {
        long duration = 1000; // 默认最小耗时

        if (entity instanceof QC) {
            QC qc = (QC) entity;
            // 模拟：假设平均作业高度30米（实际可从 Instruction 的 extraParameters 获取）
            // 调用 QC 实体中的物理计算方法
            duration = (long) qc.calculateLiftTime(30.0);
        }
        else if (entity instanceof YC) {
            YC yc = (YC) entity;
            // 模拟：假设作业层数为3层
            // 调用 YC 实体中的物理计算方法
            duration = (long) yc.calculateCycleTime(3);
        }

        return duration;
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        if (instructionId.startsWith("WAIT_COLLISION")) return;

        assignedInstructionIds.remove(instructionId);
        for (List<Instruction> q : instructionQueues.values()) {
            q.removeIf(i -> i.getInstructionId().equals(instructionId));
        }
    }

    @Override
    public Instruction resolveCollision(String entityId, String segmentId) {
        System.out.println("[Out算法] 解决 " + entityId + " 冲突 -> 等待 " + COLLISION_WAIT_MS + "ms");

        Instruction waitInst = new Instruction(
                "WAIT_COLLISION_" + System.nanoTime(),
                InstructionType.WAIT,
                "CURRENT",
                "CURRENT"
        );
        waitInst.setExpectedDuration(COLLISION_WAIT_MS);
        waitInst.setPriority(999);
        return waitInst;
    }

    private boolean isEntitySuitable(Entity entity, Instruction instruction) {
        switch (entity.getType()) {
            case QC: return entity.getId().equals(instruction.getTargetQC());
            case YC: return entity.getId().equals(instruction.getTargetYC());
            case IT: return entity.getId().equals(instruction.getTargetIT());
            default: return false;
        }
    }
}