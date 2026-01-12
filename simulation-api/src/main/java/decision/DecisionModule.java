package decision;

import entity.Entity;
import entity.EntityType;
import Instruction.Instruction;
import java.util.*;

public class DecisionModule {
    private Map<String, List<Instruction>> instructionQueues; // 按设备类型分类的指令队列
    private Map<String, Instruction> assignedInstructions; // 已分配的指令

    public DecisionModule() {
        instructionQueues = new HashMap<>();
        instructionQueues.put("QC", new ArrayList<>());
        instructionQueues.put("YC", new ArrayList<>());
        instructionQueues.put("IT", new ArrayList<>());
        assignedInstructions = new HashMap<>();
    }

    public void addInstruction(Instruction instruction) {
        // 根据指令类型添加到相应队列
        if (instruction.getTargetQC() != null) {
            instructionQueues.get("QC").add(instruction);
        } else if (instruction.getTargetYC() != null) {
            instructionQueues.get("YC").add(instruction);
        } else if (instruction.getTargetIT() != null) {
            instructionQueues.get("IT").add(instruction);
        }

        // 按优先级排序
        instructionQueues.values().forEach(queue ->
                queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed())
        );
    }

    public Instruction getNextInstruction(Entity entity) {
        List<Instruction> queue = instructionQueues.get(entity.getType().name());

        if (queue == null || queue.isEmpty()) {
            return null;
        }

        // 查找适合该设备的指令
        for (Iterator<Instruction> iterator = queue.iterator(); iterator.hasNext();) {
            Instruction instruction = iterator.next();

            // 检查指令是否已分配给其他设备
            if (assignedInstructions.containsKey(instruction.getInstructionId())) {
                continue;
            }

            // 检查设备是否匹配
            if (isEntitySuitableForInstruction(entity, instruction)) {
                iterator.remove();
                assignedInstructions.put(instruction.getInstructionId(), instruction);
                return instruction;
            }
        }

        return null;
    }

    private boolean isEntitySuitableForInstruction(Entity entity, Instruction instruction) {
        // 简化匹配逻辑
        switch (entity.getType()) {
            case QC:
                return entity.getId().equals(instruction.getTargetQC());
            case YC:
                return entity.getId().equals(instruction.getTargetYC());
            case IT:
                return entity.getId().equals(instruction.getTargetIT());
            default:
                return false;
        }
    }

    public void releaseInstruction(String instructionId) {
        assignedInstructions.remove(instructionId);
    }

    public List<Instruction> getPendingInstructions(String entityType) {
        return new ArrayList<>(instructionQueues.getOrDefault(entityType, new ArrayList<>()));
    }

    public int getQueueSize(String entityType) {
        return instructionQueues.getOrDefault(entityType, new ArrayList<>()).size();
    }

    public int getTotalPendingInstructions() {
        return instructionQueues.values().stream()
                .mapToInt(List::size)
                .sum();
    }
}