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

    // Key: InstructionID, Value: Set of EntityIDs who finished their part
    private final Map<String, Set<String>> finishedParts = new ConcurrentHashMap<>();

    @Override
    public void onNewTaskSubmitted(Instruction instruction) {
        if (instruction == null || instruction.getInstructionId() == null) {
            System.err.println("错误: 提交了无效的任务 (ID为空)");
            return;
        }
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
                if (taskId == null) continue; // 防御性编程

                if ("COMPLETED".equals(task.getStatus())) continue;

                // 检查该设备是否已经完成了它在这个任务中的部分
                Set<String> finished = finishedParts.getOrDefault(taskId, Collections.emptySet());
                if (finished.contains(entity.getId())) continue;

                if (isEntityMatch(entity, task)) {
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
            pendingTasks.removeIf(t -> instructionId.equals(t.getInstructionId()) && "COMPLETED".equals(t.getStatus()));
        }
        finishedParts.remove(instructionId);
    }

    // 通知Dispatcher某个设备完成了该任务的某个环节
    public void markPartCompleted(String instructionId, String entityId) {
        if (instructionId == null || entityId == null) return;
        finishedParts.computeIfAbsent(instructionId, k -> ConcurrentHashMap.newKeySet()).add(entityId);
    }

    @Override
    public Instruction checkInterruption(Entity entity) { return null; }

    @Override
    public Instruction resolveCollision(Entity entity, String obstacleId) {
        Instruction wait = new Instruction("WAIT_" + System.nanoTime() + "_" + entity.getId(), InstructionType.WAIT, null, null);
        wait.setExpectedDuration(2000);
        return wait;
    }

    private boolean isEntityMatch(Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) return e.getId().equals(i.getTargetQC());
        if (e.getType() == EntityType.YC) return e.getId().equals(i.getTargetYC());
        if (e.getType() == EntityType.IT) return i.getTargetIT() != null && e.getId().equals(i.getTargetIT());
        return false;
    }
}