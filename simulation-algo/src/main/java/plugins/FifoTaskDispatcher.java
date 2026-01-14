// 文件: simulation-algo/src/main/java/plugins/FifoTaskDispatcher.java
package plugins;

import decision.TaskDispatcher;
import entity.Entity;
import entity.QC;
import entity.YC;
import Instruction.Instruction;
import Instruction.InstructionType;
import java.util.*;

public class FifoTaskDispatcher implements TaskDispatcher {

    // 状态标志：是否处于紧急暂停模式
    private volatile boolean emergencyStop = false;

    private final Map<String, List<Instruction>> instructionQueues;
    private final Set<String> assignedInstructionIds;

    // 暂停时的忙等待时间 (ms)
    private static final long PAUSE_WAIT_DURATION = 1000L;

    public FifoTaskDispatcher() {
        this.instructionQueues = new HashMap<>();
        this.instructionQueues.put("QC", new ArrayList<>());
        this.instructionQueues.put("YC", new ArrayList<>());
        this.instructionQueues.put("IT", new ArrayList<>());
        this.instructionQueues.put("UNKNOWN", new ArrayList<>());
        this.assignedInstructionIds = new HashSet<>();
    }

    // 【新增】控制方法：设置紧急暂停
    @Override
    public void setEmergencyStop(boolean stop) {
        this.emergencyStop = stop;
        System.out.println(">>> 紧急暂停模式: " + (stop ? "开启 (所有设备将停止)" : "关闭 (恢复作业)"));
    }

    @Override
    public void onNewTaskSubmitted(Instruction instruction) {
        String queueKey = "UNKNOWN";
        if (instruction.getTargetQC() != null) queueKey = "QC";
        else if (instruction.getTargetYC() != null) queueKey = "YC";
        else if (instruction.getTargetIT() != null) queueKey = "IT";

        List<Instruction> queue = instructionQueues.computeIfAbsent(queueKey, k -> new ArrayList<>());
        queue.add(instruction);
        queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed());
    }

    @Override
    public Instruction assignTask(Entity entity) {
        // 1. 如果处于紧急暂停模式，不分配新任务，而是让实体原地等待
        if (emergencyStop) {
            return createPauseInstruction(entity.getCurrentPosition());
        }

        // 2. 正常分配逻辑
        List<Instruction> queue = instructionQueues.get(entity.getType().name());
        if (queue == null || queue.isEmpty()) return null;

        for (Instruction instruction : queue) {
            // 跳过已被分配的任务
            if (assignedInstructionIds.contains(instruction.getInstructionId())) continue;

            if (isEntitySuitable(entity, instruction)) {
                assignedInstructionIds.add(instruction.getInstructionId());
                instruction.assignToIT(entity.getId());
                instruction.setExpectedDuration(calculateDuration(entity, instruction));
                return instruction;
            }
        }
        return null;
    }

    @Override
    public Instruction checkInterruption(Entity entity) {
        // 1. 只有在紧急暂停开启，且实体并未处于等待状态时，才触发中断
        if (emergencyStop) {
            System.out.println("[调度] 触发中断: " + entity.getId());

            // 2. 【关键】释放当前正在执行的任务，使其能被重新分配
            String currentInstId = entity.getCurrentInstructionId();
            if (currentInstId != null && assignedInstructionIds.contains(currentInstId)) {
                // 从已分配列表中移除
                assignedInstructionIds.remove(currentInstId);

                // 将任务状态重置为 PENDING，确保下次能被正确扫描到
                resetInstructionStatus(currentInstId);

                System.out.println("[调度] 任务 " + currentInstId + " 已回退到等待队列");
            }

            // 3. 返回一个高优先级的等待指令
            return createPauseInstruction(entity.getCurrentPosition());
        }
        return null;
    }

    // 创建一个短时的等待指令 (忙等待)
    private Instruction createPauseInstruction(String position) {
        Instruction pause = new Instruction(
                "EMERGENCY_PAUSE_" + System.nanoTime(),
                InstructionType.WAIT,
                position,
                position
        );
        pause.setExpectedDuration(PAUSE_WAIT_DURATION);
        pause.setPriority(999);
        return pause;
    }

    // 辅助：重置任务状态
    private void resetInstructionStatus(String instructionId) {
        for (List<Instruction> list : instructionQueues.values()) {
            for (Instruction inst : list) {
                if (inst.getInstructionId().equals(instructionId)) {
                    inst.setStatus("PENDING");
                    inst.setTargetIT(null); // 解绑设备
                    return;
                }
            }
        }
    }

    @Override
    public void onTaskCompleted(String instructionId) {
        if (instructionId.startsWith("WAIT_COLLISION") || instructionId.startsWith("EMERGENCY_PAUSE")) return;

        assignedInstructionIds.remove(instructionId);
        for (List<Instruction> q : instructionQueues.values()) {
            q.removeIf(i -> i.getInstructionId().equals(instructionId));
        }
    }

    @Override
    public Instruction resolveCollision(String entityId, String segmentId) {
        // 冲突时等待 5秒
        Instruction waitInst = new Instruction("WAIT_COLLISION_" + System.nanoTime(), InstructionType.WAIT, "CURRENT", "CURRENT");
        waitInst.setExpectedDuration(5000L);
        return waitInst;
    }

    private long calculateDuration(Entity entity, Instruction instruction) {
        // ... (保持原有逻辑不变)
        if (instruction.getExpectedDuration() > 0) return instruction.getExpectedDuration();
        return 1000;
    }

    private boolean isEntitySuitable(Entity entity, Instruction instruction) {
        // ... (保持原有逻辑不变)
        switch (entity.getType()) {
            case QC: return entity.getId().equals(instruction.getTargetQC());
            case YC: return entity.getId().equals(instruction.getTargetYC());
            case IT: return entity.getId().equals(instruction.getTargetIT());
            default: return false;
        }
    }
}