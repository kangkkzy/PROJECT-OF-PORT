package decision;

import entity.Entity;
import Instruction.Instruction;
import Instruction.InstructionType;
import map.PortMap; // 需要依赖地图进行路径规划

import java.util.*;

/**
 * 本地决策引擎实现
 * [修正] 实现了路径规划和冲突解决逻辑
 */
public class LocalDecisionEngine implements ExternalTaskService {
    private Map<String, List<Instruction>> instructionQueues;
    private Map<String, Instruction> assignedInstructions;
    private PortMap portMap; // [新增] 持有地图引用用于路径规划

    public LocalDecisionEngine(PortMap portMap) {
        this.portMap = portMap;
        this.instructionQueues = new HashMap<>();
        this.instructionQueues.put("QC", new ArrayList<>());
        this.instructionQueues.put("YC", new ArrayList<>());
        this.instructionQueues.put("IT", new ArrayList<>());
        this.instructionQueues.put("UNKNOWN", new ArrayList<>());
        this.assignedInstructions = new HashMap<>();
    }

    @Override
    public void submitTask(Instruction instruction) {
        if (instruction.getTargetQC() != null) {
            instructionQueues.get("QC").add(instruction);
        } else if (instruction.getTargetYC() != null) {
            instructionQueues.get("YC").add(instruction);
        } else if (instruction.getTargetIT() != null) {
            instructionQueues.get("IT").add(instruction);
        } else {
            System.err.println("警告: 任务 " + instruction.getInstructionId() + " 未指定目标设备，存入待定队列。");
            instructionQueues.get("UNKNOWN").add(instruction);
        }

        instructionQueues.values().forEach(queue ->
                queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed())
        );
    }

    @Override
    public Instruction askForNewTask(Entity entity) {
        List<Instruction> queue = instructionQueues.get(entity.getType().name());
        if (queue == null || queue.isEmpty()) return null;

        for (Instruction instruction : queue) {
            if (assignedInstructions.containsKey(instruction.getInstructionId())) continue;

            if (isEntitySuitableForInstruction(entity, instruction)) {
                assignedInstructions.put(instruction.getInstructionId(), instruction);
                instruction.assignToIT(entity.getId()); // 标记分配
                return instruction;
            }
        }
        return null;
    }

    @Override
    public void reportTaskCompletion(String instructionId, String entityId) {
        // 只有真正的任务才移除，临时生成的冲突解决指令(WAIT)不一定在队列里
        if (assignedInstructions.containsKey(instructionId)) {
            assignedInstructions.remove(instructionId);
            removeFromQueue(instructionId);
        }
    }

    /**
     * [新增] 路径规划实现
     * 简单调用 PortMap 的 BFS 算法，实际可替换为 A* 或其他外部算法
     */
    @Override
    public List<String> findPath(String origin, String destination) {
        if (portMap == null) return Collections.emptyList();
        return portMap.findPath(origin, destination);
    }

    /**
     * [新增] 冲突解决策略
     * 简单策略：让实体原地等待 5 秒 (WAIT 指令)
     */
    @Override
    public Instruction resolveCollision(Entity entity, Instruction currentInstruction) {
        String waitId = "WAIT_" + entity.getId() + "_" + System.currentTimeMillis();
        // 创建一个原地等待指令
        Instruction waitInst = new Instruction(waitId, InstructionType.WAIT, entity.getCurrentPosition(), entity.getCurrentPosition());

        // 如果原指令存在，等待结束后希望继续执行原指令逻辑(这里简化为仅返回等待，Scheduler需处理状态)
        // 在仿真中，通常等待完后，实体会再次请求任务或重新尝试当前步骤

        System.out.println("决策引擎: 实体 " + entity.getId() + " 发生冲突，下发等待指令(5s)。");
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
}