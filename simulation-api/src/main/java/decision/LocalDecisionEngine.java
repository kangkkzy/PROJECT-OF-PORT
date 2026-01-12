package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.*;

/**
 * 本地决策引擎 (实现了 ExternalTaskService 接口)
 * 作用：充当"外接模块"的本地替身，保留原有的逻辑以便测试
 */
public class LocalDecisionEngine implements ExternalTaskService {
    // 复用原 DecisionModule 的数据结构
    private Map<String, List<Instruction>> instructionQueues;
    private Map<String, Instruction> assignedInstructions;

    public LocalDecisionEngine() {
        instructionQueues = new HashMap<>();
        instructionQueues.put("QC", new ArrayList<>());
        instructionQueues.put("YC", new ArrayList<>());
        instructionQueues.put("IT", new ArrayList<>());
        assignedInstructions = new HashMap<>();
    }

    @Override
    public void submitTask(Instruction instruction) {
        // 原 addInstruction 逻辑
        if (instruction.getTargetQC() != null) {
            instructionQueues.get("QC").add(instruction);
        } else if (instruction.getTargetYC() != null) {
            instructionQueues.get("YC").add(instruction);
        } else if (instruction.getTargetIT() != null) {
            instructionQueues.get("IT").add(instruction);
        }
        // 重新排序
        instructionQueues.values().forEach(queue ->
                queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed())
        );
    }

    @Override
    public Instruction askForNewTask(Entity entity) {
        // 原 getNextInstruction 逻辑
        List<Instruction> queue = instructionQueues.get(entity.getType().name());
        if (queue == null || queue.isEmpty()) return null;

        for (Iterator<Instruction> iterator = queue.iterator(); iterator.hasNext();) {
            Instruction instruction = iterator.next();
            if (assignedInstructions.containsKey(instruction.getInstructionId())) continue;

            if (isEntitySuitableForInstruction(entity, instruction)) {
                // 模拟外部系统：决定分配任务后，将其移出等待队列或标记为已分配
                // 这里我们选择不移除，只放入 assigned map，防止重复分配
                assignedInstructions.put(instruction.getInstructionId(), instruction);
                return instruction;
            }
        }
        return null;
    }

    @Override
    public void reportTaskCompletion(String instructionId, String entityId) {
        // 原 releaseInstruction 逻辑
        assignedInstructions.remove(instructionId);

        // 额外清理：在本地逻辑中，任务完成了就应该从队列彻底移除
        // 注意：这步操作在纯外部接口中可能不需要，但在本地模拟中需要清理内存
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