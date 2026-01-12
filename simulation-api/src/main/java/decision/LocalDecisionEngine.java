package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.*;

/**
 * 本地决策引擎 (修正版)
 * 职责：
 * 1. 任务队列管理 (Task Queueing)
 * 2. 任务指派 (Dispatching)
 * 3. 路由代理 (Route Proxy) - 仅仅转发请求，不包含算法
 */
public class LocalDecisionEngine implements ExternalTaskService {

    // 任务队列
    private Map<String, List<Instruction>> instructionQueues;
    private Map<String, Instruction> assignedInstructions;

    // 依赖接口，而非具体实现
    private final RoutePlanner routePlanner;

    /**
     * 构造函数注入 RoutePlanner
     * @param routePlanner 具体的路径规划器实现
     */
    public LocalDecisionEngine(RoutePlanner routePlanner) {
        this.routePlanner = routePlanner;

        this.instructionQueues = new HashMap<>();
        this.instructionQueues.put("QC", new ArrayList<>());
        this.instructionQueues.put("YC", new ArrayList<>());
        this.instructionQueues.put("IT", new ArrayList<>());
        this.instructionQueues.put("UNKNOWN", new ArrayList<>());
        this.assignedInstructions = new HashMap<>();
    }

    @Override
    public List<String> getRoute(String origin, String destination) {
        // === 核心修正 ===
        // 只有接口调用，没有具体算法逻辑
        return routePlanner.searchRoute(origin, destination);
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
            System.err.println("警告: 任务未指定设备，归入 UNKNOWN 队列");
            instructionQueues.get("UNKNOWN").add(instruction);
        }

        // 简单的优先级排序
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
                instruction.assignToIT(entity.getId());
                return instruction;
            }
        }
        return null;
    }

    @Override
    public void reportTaskCompletion(String instructionId, String entityId) {
        if (assignedInstructions.containsKey(instructionId)) {
            assignedInstructions.remove(instructionId);
            removeFromQueue(instructionId);
        }
    }

    @Override
    public void reportCollision(String entityId, String segmentId) {
        // 简单记录，不处理复杂逻辑
        System.out.println("决策层收到冲突报告: 实体 " + entityId + " 在路段 " + segmentId);
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