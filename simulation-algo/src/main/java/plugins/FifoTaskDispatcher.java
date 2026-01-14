package plugins;

import decision.TaskAllocator;
import decision.TrafficController;
import entity.Entity;
import entity.EntityType;
import Instruction.Instruction;
import Instruction.InstructionType;
import java.util.*;

public class FifoTaskDispatcher implements TaskAllocator, TrafficController {

    // --- 任务分配相关状态 ---
    private final Map<EntityType, List<Instruction>> instructionQueues;
    private final Set<String> assignedInstructionIds;

    // --- 交通控制相关状态 ---
    private volatile boolean emergencyStop = false;
    private static final long PAUSE_WAIT_DURATION = 1000L;

    public FifoTaskDispatcher() {
        this.instructionQueues = new HashMap<>();
        for (EntityType type : EntityType.values()) {
            this.instructionQueues.put(type, new ArrayList<>());
        }
        this.assignedInstructionIds = new HashSet<>();
    }

    // ================= TaskAllocator 实现 =================

    @Override
    public void onNewTaskSubmitted(Instruction instruction) {
        EntityType targetType = EntityType.IT; // 默认为 IT
        if (instruction.getTargetIT() != null) targetType = EntityType.IT;
        else if (instruction.getTargetYC() != null) targetType = EntityType.YC;
        else if (instruction.getTargetQC() != null) targetType = EntityType.QC;

        List<Instruction> queue = instructionQueues.get(targetType);
        synchronized (queue) {
            queue.add(instruction);
            queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed());
        }
        System.out.println("[Allocator] 任务 " + instruction.getInstructionId() + " 加入队列: " + targetType);
    }

    @Override
    public Instruction assignTask(Entity entity) {
        if (emergencyStop) {
            // 修改点 1: getCurrentPositionStr() -> getCurrentPosition()
            return createPauseInstruction(entity.getCurrentPosition());
        }

        List<Instruction> queue = instructionQueues.get(entity.getType());
        if (queue == null) return null;

        synchronized (queue) {
            for (Instruction instruction : queue) {
                if (assignedInstructionIds.contains(instruction.getInstructionId())) continue;

                if (isEntitySuitable(entity, instruction)) {
                    assignedInstructionIds.add(instruction.getInstructionId());
                    instruction.assignToIT(entity.getId());
                    // 简单估算时长
                    instruction.setExpectedDuration(instruction.getExpectedDuration() > 0 ? instruction.getExpectedDuration() : 1000);
                    return instruction;
                }
            }
        }
        return null;
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        if (instructionId.startsWith("WAIT") || instructionId.startsWith("EMERGENCY")) return;
        assignedInstructionIds.remove(instructionId);
        // 清理队列
        for (List<Instruction> q : instructionQueues.values()) {
            synchronized (q) {
                q.removeIf(i -> i.getInstructionId().equals(instructionId));
            }
        }
    }

    // ================= TrafficController 实现 =================

    @Override
    public void setEmergencyStop(boolean stop) {
        this.emergencyStop = stop;
    }

    @Override
    public Instruction checkInterruption(Entity entity) {
        if (emergencyStop) {
            // 如果紧急停止，且任务已分配，则释放任务回队列
            String currentInstId = entity.getCurrentInstructionId();
            if (currentInstId != null && assignedInstructionIds.contains(currentInstId)) {
                assignedInstructionIds.remove(currentInstId);
                // 重置任务状态逻辑略...
                System.out.println("[Traffic] 任务 " + currentInstId + " 因紧急停止被中断");
            }
            // 修改点 2: getCurrentPositionStr() -> getCurrentPosition()
            return createPauseInstruction(entity.getCurrentPosition());
        }
        return null;
    }

    @Override
    public Instruction resolveCollision(Entity entity, String obstacleId) {
        // 简单策略：等待 5 秒
        Instruction wait = new Instruction("WAIT_COLLISION_" + System.nanoTime(), InstructionType.WAIT, "CURRENT", "CURRENT");
        wait.setExpectedDuration(5000L);
        return wait;
    }

    // ================= 辅助方法 =================

    private Instruction createPauseInstruction(String pos) {
        Instruction pause = new Instruction("EMERGENCY_" + System.nanoTime(), InstructionType.WAIT, pos, pos);
        pause.setExpectedDuration(PAUSE_WAIT_DURATION);
        pause.setPriority(999);
        return pause;
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