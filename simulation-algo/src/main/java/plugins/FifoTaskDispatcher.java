package plugins;

import decision.TaskDispatcher;
import entity.Entity;
import Instruction.Instruction;
import Instruction.InstructionType;
import java.util.*;

// 实现 TaskDispatcher 接口
public class FifoTaskDispatcher implements TaskDispatcher {

    // 算法策略常量
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

        // 如果没有预设时间，算法可以在这里赋予默认值
        if (instruction.getExpectedDuration() == 0) {
            instruction.setExpectedDuration(5000);
        }

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
                return instruction;
            }
        }
        return null;
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        // 忽略为了避让而生成的临时指令
        if (instructionId.startsWith("WAIT_COLLISION")) return;

        assignedInstructionIds.remove(instructionId);
        for (List<Instruction> q : instructionQueues.values()) {
            q.removeIf(i -> i.getInstructionId().equals(instructionId));
        }
    }

    @Override
    public Instruction resolveCollision(String entityId, String segmentId) {
        // 策略：返回一个高优先级的等待指令
        System.out.println("[Out算法] 解决 " + entityId + " 冲突 -> 等待 " + COLLISION_WAIT_MS + "ms");

        Instruction waitInst = new Instruction(
                "WAIT_COLLISION_" + System.nanoTime(),
                InstructionType.WAIT,
                "CURRENT",
                "CURRENT"
        );
        waitInst.setExpectedDuration(COLLISION_WAIT_MS);
        waitInst.setPriority(999); // 最高优先级确保立即执行
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