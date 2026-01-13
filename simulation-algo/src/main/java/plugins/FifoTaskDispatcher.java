package plugins;

import decision.TaskDispatcher;
import entity.Entity;
import entity.QC;
import entity.YC;
import Instruction.Instruction;
import Instruction.InstructionType;
import java.util.*;

public class FifoTaskDispatcher implements TaskDispatcher {

    // 默认策略参数 (当指令未指定具体参数时使用)
    private static final double DEFAULT_QC_LIFT_HEIGHT = 30.0;
    private static final int DEFAULT_YC_TIER = 3;
    private static final long DEFAULT_COLLISION_WAIT_MS = 5000L;

    // 参数键名定义 (需与 Tasks.json 中的 parameters 键名一致)
    private static final String PARAM_LIFT_HEIGHT = "liftHeight";
    private static final String PARAM_TARGET_TIER = "targetTier";
    private static final String PARAM_COLLISION_WAIT = "collisionWaitMs";

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

                // 动态计算耗时：基于任务参数和设备物理模型
                long duration = calculateDuration(entity, instruction);
                instruction.setExpectedDuration(duration);

                return instruction;
            }
        }
        return null;
    }

    /**
     * 根据设备性能模型及任务参数计算作业耗时
     */
    private long calculateDuration(Entity entity, Instruction instruction) {
        // 如果指令中直接指定了预期耗时，则优先使用 (例如 WAIT 指令)
        if (instruction.getExpectedDuration() > 0) {
            return instruction.getExpectedDuration();
        }

        long duration = 1000; // 默认最小耗时作为兜底

        if (entity instanceof QC) {
            QC qc = (QC) entity;
            // 尝试从任务参数中获取作业高度，如果不存在则使用默认策略值
            double liftHeight = getDoubleParam(instruction, PARAM_LIFT_HEIGHT, DEFAULT_QC_LIFT_HEIGHT);
            // 调用 QC 实体中的物理计算方法
            duration = (long) qc.calculateLiftTime(liftHeight);
        }
        else if (entity instanceof YC) {
            YC yc = (YC) entity;
            // 尝试从任务参数中获取作业层数
            int tier = getIntParam(instruction, PARAM_TARGET_TIER, DEFAULT_YC_TIER);
            // 调用 YC 实体中的物理计算方法
            duration = (long) yc.calculateCycleTime(tier);
        }

        return duration;
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        // 忽略内部生成的冲突等待指令
        if (instructionId.startsWith("WAIT_COLLISION")) return;

        assignedInstructionIds.remove(instructionId);
        for (List<Instruction> q : instructionQueues.values()) {
            q.removeIf(i -> i.getInstructionId().equals(instructionId));
        }
    }

    @Override
    public Instruction resolveCollision(String entityId, String segmentId) {
        // 可以在这里扩展更复杂的冲突解决策略，比如重路由
        // 目前策略：简单的等待，等待时间可配置

        long waitTime = DEFAULT_COLLISION_WAIT_MS;
        // 也可以尝试从全局配置或特定指令中获取冲突等待时间，这里暂用常量作为策略默认值

        System.out.println("[Out算法] 解决 " + entityId + " 冲突 -> 等待 " + waitTime + "ms");

        Instruction waitInst = new Instruction(
                "WAIT_COLLISION_" + System.nanoTime(),
                InstructionType.WAIT,
                "CURRENT",
                "CURRENT"
        );
        waitInst.setExpectedDuration(waitTime);
        waitInst.setPriority(999); // 最高优先级，立即执行
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

    // 辅助方法：从 Instruction 的 extraParameters 中安全获取参数
    private double getDoubleParam(Instruction inst, String key, double defaultValue) {
        Object val = inst.getExtraParameter(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return defaultValue;
    }

    private int getIntParam(Instruction inst, String key, int defaultValue) {
        Object val = inst.getExtraParameter(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return defaultValue;
    }
}