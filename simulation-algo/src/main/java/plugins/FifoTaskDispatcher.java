package plugins;

import decision.TaskAllocator;
import decision.TrafficController;
import entity.Entity;
import entity.EntityType;
import Instruction.Instruction;
import Instruction.InstructionType;
import java.util.*;

// 这个类现在同时充当“分配器”和“交通指挥”，符合单一插件类实现多接口的模式
public class FifoTaskDispatcher implements TaskAllocator, TrafficController {

    private final Map<EntityType, List<Instruction>> instructionQueues = new HashMap<>();
    private final Set<String> assignedInstructionIds = new HashSet<>();
    private volatile boolean emergencyStop = false;

    public FifoTaskDispatcher() {
        for (EntityType type : EntityType.values()) {
            instructionQueues.put(type, new ArrayList<>());
        }
    }

    // --- TaskAllocator 实现 ---
    @Override
    public void onNewTaskSubmitted(Instruction instruction) {
        EntityType targetType = determineTargetType(instruction);
        synchronized (instructionQueues) {
            instructionQueues.get(targetType).add(instruction);
            // 简单的优先级排序决策
            instructionQueues.get(targetType).sort(Comparator.comparingInt(Instruction::getPriority).reversed());
        }
    }

    @Override
    public Instruction assignTask(Entity entity) {
        if (emergencyStop) return null; // 决策：紧急停止时不分配新任务

        List<Instruction> queue = instructionQueues.get(entity.getType());
        synchronized (instructionQueues) {
            for (Instruction instruction : queue) {
                if (assignedInstructionIds.contains(instruction.getInstructionId())) continue;

                // 决策：判断该实体是否匹配任务要求（如指定ID）
                if (isEntitySuitable(entity, instruction)) {
                    assignedInstructionIds.add(instruction.getInstructionId());
                    instruction.assignToIT(entity.getId());
                    return instruction;
                }
            }
        }
        return null;
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        assignedInstructionIds.remove(instructionId);
        synchronized (instructionQueues) {
            for (List<Instruction> queue : instructionQueues.values()) {
                queue.removeIf(i -> i.getInstructionId().equals(instructionId));
            }
        }
    }

    // --- TrafficController 实现 ---
    @Override
    public Instruction checkInterruption(Entity entity) {
        // 决策：如果全局紧急停止，发出原地等待指令
        if (emergencyStop) {
            Instruction wait = new Instruction("EMERGENCY_" + System.nanoTime(), InstructionType.WAIT, null, null);
            wait.setExpectedDuration(1000);
            return wait;
        }
        return null;
    }

    @Override
    public Instruction resolveCollision(Entity entity, String obstacleId) {
        // 决策：遇到冲突，简单的策略是等待 5秒
        Instruction wait = new Instruction("COLLISION_WAIT_" + System.nanoTime(), InstructionType.WAIT, null, null);
        wait.setExpectedDuration(5000);
        return wait;
    }

    // 辅助方法
    private EntityType determineTargetType(Instruction i) {
        if (i.getTargetQC() != null) return EntityType.QC;
        if (i.getTargetYC() != null) return EntityType.YC;
        return EntityType.IT;
    }

    private boolean isEntitySuitable(Entity e, Instruction i) {
        // 简化的匹配逻辑
        if (e.getType() == EntityType.QC && e.getId().equals(i.getTargetQC())) return true;
        if (e.getType() == EntityType.YC && e.getId().equals(i.getTargetYC())) return true;
        return e.getType() == EntityType.IT;
    }

    // 供外部设置（非接口方法，可由Main调用或通过配置注入）
    public void setEmergencyStop(boolean stop) { this.emergencyStop = stop; }
}