package algo;

import decision.TaskDispatcher;
import entity.Entity;
import Instruction.Instruction;
import java.util.*;

/**
 * 基于 FIFO (先进先出) 和 优先级 的简单调度器
 * 原 LocalDecisionEngine 的逻辑被移动到了这里
 */
public class FifoTaskDispatcher implements TaskDispatcher {

    // 内部维护任务队列（算法的状态）
    private final Map<String, List<Instruction>> instructionQueues;
    // 记录已分配但未完成的任务，防止重复分配
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
        // 1. 任务安排 (Arrangement): 分类
        String queueKey = "UNKNOWN";
        if (instruction.getTargetQC() != null) {
            queueKey = "QC";
        } else if (instruction.getTargetYC() != null) {
            queueKey = "YC";
        } else if (instruction.getTargetIT() != null) {
            queueKey = "IT";
        }

        List<Instruction> queue = instructionQueues.get(queueKey);
        queue.add(instruction);

        // 2. 任务安排 (Arrangement): 排序 (优先级高的在前)
        queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed());

        System.out.println("调度器: 已接收任务 " + instruction.getInstructionId() + " 进入 " + queueKey + " 队列");
    }

    @Override
    public Instruction assignTask(Entity entity) {
        // 3. 任务指派 (Dispatching): 匹配
        List<Instruction> queue = instructionQueues.get(entity.getType().name());
        if (queue == null || queue.isEmpty()) return null;

        for (Instruction instruction : queue) {
            // 跳过已分配的任务
            if (assignedInstructionIds.contains(instruction.getInstructionId())) continue;

            // 核心匹配逻辑
            if (isEntitySuitableForInstruction(entity, instruction)) {
                // 标记为已分配
                assignedInstructionIds.add(instruction.getInstructionId());
                instruction.assignToIT(entity.getId()); // 更新指令状态
                return instruction;
            }
        }
        return null;
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        // 清理状态
        assignedInstructionIds.remove(instructionId);
        removeFromQueue(instructionId);
    }

    private void removeFromQueue(String instructionId) {
        for (List<Instruction> queue : instructionQueues.values()) {
            queue.removeIf(i -> i.getInstructionId().equals(instructionId));
        }
    }

    private boolean isEntitySuitableForInstruction(Entity entity, Instruction instruction) {
        switch (entity.getType()) {
            case QC: return entity.getId().equals(instruction.getTargetQC());
            case YC: return entity.getId().equals(instruction.getTargetYC());
            case IT: return entity.getId().equals(instruction.getTargetIT());
            default: return false;
        }
    }
}
