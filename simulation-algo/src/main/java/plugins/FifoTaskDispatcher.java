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
    private final List<Instruction> pendingTasks = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Set<String>> taskAssignments = new ConcurrentHashMap<>();
    // 新增：记录每个任务已完成的设备ID
    private final Map<String, Set<String>> finishedParts = new ConcurrentHashMap<>();

    @Override
    public void onNewTaskSubmitted(Instruction instruction) {
        if (instruction == null || instruction.getInstructionId() == null) return;
        synchronized (pendingTasks) {
            pendingTasks.add(instruction);
            pendingTasks.sort((t1, t2) -> Integer.compare(t2.getPriority(), t1.getPriority()));
        }
    }

    @Override
    public Instruction assignTask(Entity entity) {
        synchronized (pendingTasks) {
            for (Instruction task : pendingTasks) {
                String taskId = task.getInstructionId();
                if ("COMPLETED".equals(task.getStatus())) continue;

                // 检查：如果该设备已经完成了它在这个任务中的部分，不要再返回这个任务
                Set<String> finished = finishedParts.getOrDefault(taskId, Collections.emptySet());
                if (finished.contains(entity.getId())) continue;

                Set<String> assigned = taskAssignments.computeIfAbsent(taskId, k -> new HashSet<>());

                // 允许重入（正在执行中继续请求）
                if (assigned.contains(entity.getId())) return task;

                if (isEntityMatch(entity, task)) {
                    assigned.add(entity.getId());
                    return task;
                }
            }
        }
        return null;
    }

    // 新增方法：标记部分完成
    public void markPartCompleted(String instructionId, String entityId) {
        finishedParts.computeIfAbsent(instructionId, k -> ConcurrentHashMap.newKeySet()).add(entityId);
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        if (instructionId == null) return;
        synchronized (pendingTasks) {
            pendingTasks.removeIf(t -> instructionId.equals(t.getInstructionId()));
            taskAssignments.remove(instructionId);
            finishedParts.remove(instructionId);
        }
    }

    @Override
    public Instruction checkInterruption(Entity entity) { return null; }

    @Override
    public Instruction resolveCollision(Entity entity, String obstacleId) {
        Instruction wait = new Instruction("WAIT_" + System.nanoTime(), InstructionType.WAIT, null, null);
        wait.setExpectedDuration(1000);
        return wait;
    }

    private boolean isEntityMatch(Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) return e.getId().equals(i.getTargetQC());
        if (e.getType() == EntityType.YC) return e.getId().equals(i.getTargetYC());
        if (e.getType() == EntityType.IT) return i.getTargetIT() != null && e.getId().equals(i.getTargetIT());
        return false;
    }
}