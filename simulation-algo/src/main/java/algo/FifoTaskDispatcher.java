package algo;

import decision.TaskDispatcher;
import entity.Entity;
import Instruction.Instruction;
import Instruction.InstructionType;
import java.util.*;

public class FifoTaskDispatcher implements TaskDispatcher {

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

        // [算法逻辑] 可以在这里根据任务类型计算标准耗时
        if (instruction.getExpectedDuration() == 0) {
            instruction.setExpectedDuration(estimateDuration(instruction));
        }

        List<Instruction> queue = instructionQueues.get(queueKey);
        queue.add(instruction);
        // [修复] 这里使用 Instruction 中新加的 getPriority
        queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed());

        System.out.println("FIFO调度: 接收任务 " + instruction.getInstructionId());
    }

    @Override
    public Instruction assignTask(Entity entity) {
        List<Instruction> queue = instructionQueues.get(entity.getType().name());
        if (queue == null || queue.isEmpty()) return null;

        for (Instruction instruction : queue) {
            if (assignedInstructionIds.contains(instruction.getInstructionId())) continue;

            if (isEntitySuitableForInstruction(entity, instruction)) {
                assignedInstructionIds.add(instruction.getInstructionId());
                instruction.assignToIT(entity.getId());
                return instruction;
            }
        }
        return null;
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        if (instructionId.startsWith("COLLISION_WAIT")) return;
        assignedInstructionIds.remove(instructionId);
        removeFromQueue(instructionId);
    }

    // [实现] 解决冲突的算法逻辑
    @Override
    public Instruction resolveCollision(String entityId, String segmentId) {
        // 策略：原地等待 5 秒
        String waitId = "COLLISION_WAIT_" + entityId + "_" + System.currentTimeMillis();
        Instruction waitInst = new Instruction(waitId, InstructionType.WAIT, "CURRENT", "CURRENT");

        // 算法明确指定等待时间
        waitInst.setExpectedDuration(5000L);
        waitInst.setPriority(99);

        System.out.println("FIFO调度: 生成避让指令 (等待5s) -> " + entityId);
        return waitInst;
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

    // 简易的业务耗时估算
    private long estimateDuration(Instruction i) {
        switch (i.getType()) {
            case LOAD_TO_SHIP: return 45000;
            case UNLOAD_FROM_SHIP: return 45000;
            case YARD_TO_YARD: return 30000;
            default: return 5000;
        }
    }
}