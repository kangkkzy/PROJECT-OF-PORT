package plugins;

import decision.TaskAllocator;
import decision.TrafficController;
import entity.Entity;
import entity.EntityType;
import Instruction.Instruction;
import Instruction.InstructionType;
import java.util.*;

public class FifoTaskDispatcher implements TaskAllocator, TrafficController {
    private final List<Instruction> pendingTasks = new ArrayList<>();
    // 追踪每个任务已被哪些设备领用，防止重复领用
    private final Map<String, Set<String>> taskAssignments = new HashMap<>();

    @Override
    public void onNewTaskSubmitted(Instruction instruction) {
        synchronized (pendingTasks) {
            pendingTasks.add(instruction);
            pendingTasks.sort(Comparator.comparingInt(Instruction::getPriority).reversed());
        }
    }

    @Override
    public Instruction assignTask(Entity entity) {
        synchronized (pendingTasks) {
            for (Instruction task : pendingTasks) {
                Set<String> assignedEntities = taskAssignments.computeIfAbsent(task.getInstructionId(), k -> new HashSet<>());

                // 如果该设备已经领过这个任务，跳过
                if (assignedEntities.contains(entity.getId())) continue;

                // 检查设备是否匹配任务要求
                if (isEntitySuitable(entity, task)) {
                    assignedEntities.add(entity.getId());
                    return task;
                }
            }
        }
        return null;
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        synchronized (pendingTasks) {
            pendingTasks.removeIf(t -> t.getInstructionId().equals(instructionId));
            taskAssignments.remove(instructionId);
        }
    }

    @Override
    public Instruction checkInterruption(Entity entity) { return null; }

    @Override
    public Instruction resolveCollision(Entity entity, String obstacleId) {
        Instruction wait = new Instruction("WAIT_" + System.nanoTime(), InstructionType.WAIT, null, null);
        wait.setExpectedDuration(3000);
        return wait;
    }

    private boolean isEntitySuitable(Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) return e.getId().equals(i.getTargetQC());
        if (e.getType() == EntityType.YC) return e.getId().equals(i.getTargetYC());
        if (e.getType() == EntityType.IT) {
            // 如果任务指定了集卡则匹配，否则任何空闲集卡均可
            return i.getTargetIT() == null || e.getId().equals(i.getTargetIT());
        }
        return false;
    }
}