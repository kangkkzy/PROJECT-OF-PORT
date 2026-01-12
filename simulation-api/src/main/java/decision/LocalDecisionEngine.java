package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.*;

public class LocalDecisionEngine implements ExternalTaskService {
    private Map<String, List<Instruction>> instructionQueues;
    private Map<String, Instruction> assignedInstructions;

    public LocalDecisionEngine() {
        instructionQueues = new HashMap<>();
        instructionQueues.put("QC", new ArrayList<>());
        instructionQueues.put("YC", new ArrayList<>());
        instructionQueues.put("IT", new ArrayList<>());
        // [修正] 增加一个默认队列，防止任务无处可去
        instructionQueues.put("UNKNOWN", new ArrayList<>());
        assignedInstructions = new HashMap<>();
    }

    @Override
    public void submitTask(Instruction instruction) {
        // [修正] 安全的任务分发，防止NullPointerException
        if (instruction.getTargetQC() != null) {
            instructionQueues.get("QC").add(instruction);
        } else if (instruction.getTargetYC() != null) {
            instructionQueues.get("YC").add(instruction);
        } else if (instruction.getTargetIT() != null) {
            instructionQueues.get("IT").add(instruction);
        } else {
            System.err.println("警告: 任务 " + instruction.getInstructionId() + " 未指定目标设备，存入待定队列。");
            instructionQueues.get("UNKNOWN").add(instruction);
        }

        // 排序
        instructionQueues.values().forEach(queue ->
                queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed())
        );
    }

    @Override
    public Instruction askForNewTask(Entity entity) {
        List<Instruction> queue = instructionQueues.get(entity.getType().name());
        if (queue == null || queue.isEmpty()) return null;

        for (Instruction instruction : queue) {
            if (assignedInstructions.containsKey(instruction.getInstructionId())) continue;

            if (isEntitySuitableForInstruction(entity, instruction)) {
                assignedInstructions.put(instruction.getInstructionId(), instruction);
                return instruction;
            }
        }
        return null;
    }

    @Override
    public void reportTaskCompletion(String instructionId, String entityId) {
        assignedInstructions.remove(instructionId);
        removeFromQueue(instructionId);
    }

    private void removeFromQueue(String instructionId) {
        for (List<Instruction> queue : instructionQueues.values()) {
            queue.removeIf(i -> i.getInstructionId().equals(instructionId));
        }
    }

    private boolean isEntitySuitableForInstruction(Entity entity, Instruction instruction) {
        // [修正] 增加 null 检查
        switch (entity.getType()) {
            case QC: return entity.getId().equals(instruction.getTargetQC());
            case YC: return entity.getId().equals(instruction.getTargetYC());
            case IT: return entity.getId().equals(instruction.getTargetIT());
            default: return false;
        }
    }
}