package plugins;

import decision.TaskAllocator;
import decision.TrafficController;
import entity.Entity;
import entity.EntityType;
import Instruction.Instruction;
import Instruction.InstructionType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FifoTaskDispatcher implements TaskAllocator, TrafficController {
    // 使用线程安全的集合
    private final List<Instruction> pendingTasks = Collections.synchronizedList(new ArrayList<>());
    // 追踪每个任务已被哪些设备领用
    private final Map<String, Set<String>> taskAssignments = new ConcurrentHashMap<>();

    @Override
    public void onNewTaskSubmitted(Instruction instruction) {
        if (instruction == null || instruction.getInstructionId() == null) return;
        synchronized (pendingTasks) {
            pendingTasks.add(instruction);
            // 优先级高的排前面
            pendingTasks.sort((t1, t2) -> Integer.compare(t2.getPriority(), t1.getPriority()));
        }
    }

    @Override
    public Instruction assignTask(Entity entity) {
        synchronized (pendingTasks) {
            for (Instruction task : pendingTasks) {
                if ("COMPLETED".equals(task.getStatus())) continue;

                String taskId = task.getInstructionId();
                if (taskId == null) continue;

                Set<String> assignedEntities = taskAssignments.computeIfAbsent(taskId, k -> new HashSet<>());

                // 修复核心BUG：如果该设备已经领过这个任务，允许它再次获取（用于多阶段执行）
                if (assignedEntities.contains(entity.getId())) {
                    return task;
                }

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
        if (instructionId == null) return;
        synchronized (pendingTasks) {
            pendingTasks.removeIf(t -> instructionId.equals(t.getInstructionId()));
            taskAssignments.remove(instructionId);
        }
    }

    @Override
    public Instruction checkInterruption(Entity entity) { return null; }

    @Override
    public Instruction resolveCollision(Entity entity, String obstacleId) {
        Instruction wait = new Instruction("WAIT_" + System.nanoTime(), InstructionType.WAIT, null, null);
        wait.setExpectedDuration(1000); // 减少等待时间，避免过度阻塞
        return wait;
    }

    private boolean isEntitySuitable(Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) return e.getId().equals(i.getTargetQC());
        if (e.getType() == EntityType.YC) return e.getId().equals(i.getTargetYC());
        if (e.getType() == EntityType.IT) {
            return i.getTargetIT() == null || e.getId().equals(i.getTargetIT());
        }
        return false;
    }
}