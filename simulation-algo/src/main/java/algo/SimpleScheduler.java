package algo;

import entity.Entity;
import entity.EntityStatus;
import entity.EntityType;
import Instruction.Instruction;
import Instruction.InstructionType;
import decision.ExternalTaskService;
import time.TimeEstimationModule;
import physics.PhysicsEngine;
import map.PortMap; // 仅用于获取 Segment 对象将 ID 转为 Segment，不用于寻路
import map.Segment;
import event.EventType;
import event.SimEvent;

import java.util.*;

public class SimpleScheduler {
    private ExternalTaskService taskService;
    private TimeEstimationModule timeModule;
    private PhysicsEngine physicsEngine;
    private PortMap portMap; // 用于辅助获取路段信息

    private Map<String, Entity> entities;
    private Map<String, Instruction> instructions;
    private PriorityQueue<SimEvent> eventQueue;

    public SimpleScheduler(ExternalTaskService taskService,
                           TimeEstimationModule timeModule,
                           PhysicsEngine physicsEngine,
                           PortMap portMap) {
        this.taskService = taskService;
        this.timeModule = timeModule;
        this.physicsEngine = physicsEngine;
        this.portMap = portMap;
        this.entities = new HashMap<>();
        this.instructions = new HashMap<>();
        this.eventQueue = new PriorityQueue<>();
    }

    public void registerEntity(Entity entity) {
        entities.put(entity.getId(), entity);
    }

    public void addInstruction(Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        taskService.submitTask(instruction);
    }

    public void addEvent(SimEvent event) { eventQueue.add(event); }
    public SimEvent getNextEvent() { return eventQueue.poll(); }
    public boolean hasEvents() { return !eventQueue.isEmpty(); }

    // ================= IT (集卡) 逻辑 =================

    public void handleITExecutionComplete(long currentTime, String itId, String instructionId) {
        handleGenericExecutionComplete(currentTime, itId, instructionId, EventType.IT_ARRIVAL);
    }

    public void handleITArrival(long currentTime, String itId, String instructionId, String targetPosition) {
        Entity it = entities.get(itId);
        if (it == null) return;
        it.setCurrentPosition(targetPosition);

        Instruction instruction = instructions.get(instructionId);
        // 如果是临时指令（如 WAIT），没有在 instructions map 中，需要特殊处理或忽略
        if (instruction == null) {
            // 可能是冲突解决产生的临时指令完成，重新尝试索取主任务
            handleITExecutionComplete(currentTime, itId, null);
            return;
        }

        // 检查是否到达关键节点
        if (targetPosition.equals(instruction.getOrigin())) {
            // 到达起点 -> 等待装货 (Handshake)
            it.setStatus(EntityStatus.WAITING);
            checkHandshakeAtPosition(currentTime, it, targetPosition, instruction);
        } else if (targetPosition.equals(instruction.getDestination())) {
            // 到达终点 -> 等待卸货
            it.setStatus(EntityStatus.WAITING);
            checkHandshakeAtPosition(currentTime, it, targetPosition, instruction);
        } else {
            // 中间点或移动完成，继续尝试执行当前任务的下一阶段
            // 这里简化处理：如果是移动指令完成，触发完成逻辑
            if (instruction.getType() == InstructionType.MOVE) {
                handleITExecutionComplete(currentTime, itId, instructionId);
            }
        }
    }

    // ================= QC (桥吊) 逻辑 (补全) =================

    public void handleQCExecutionComplete(long currentTime, String qcId, String instructionId) {
        handleGenericExecutionComplete(currentTime, qcId, instructionId, EventType.QC_ARRIVAL);
    }

    public void handleQCArrival(long currentTime, String qcId, String instructionId, String targetPosition) {
        // QC 即使移动也是为了作业，逻辑与 IT 类似，到达后检查是否可以作业
        Entity qc = entities.get(qcId);
        if (qc == null) return;
        qc.setCurrentPosition(targetPosition);
        qc.setStatus(EntityStatus.WAITING);

        Instruction instruction = instructions.get(instructionId);
        if (instruction != null) {
            checkHandshakeAtPosition(currentTime, qc, targetPosition, instruction);
        }
    }

    // ================= YC (龙门吊) 逻辑 (补全) =================

    public void handleYCExecutionComplete(long currentTime, String ycId, String instructionId) {
        handleGenericExecutionComplete(currentTime, ycId, instructionId, EventType.YC_ARRIVAL);
    }

    public void handleYCArrival(long currentTime, String ycId, String instructionId, String targetPosition) {
        Entity yc = entities.get(ycId);
        if (yc == null) return;
        yc.setCurrentPosition(targetPosition);
        yc.setStatus(EntityStatus.WAITING);

        Instruction instruction = instructions.get(instructionId);
        if (instruction != null) {
            checkHandshakeAtPosition(currentTime, yc, targetPosition, instruction);
        }
    }

    // ================= 通用逻辑 =================

    /**
     * 通用的任务完成处理：释放资源 -> 上报完成 -> 索取新任务 -> 调度新任务
     */
    private void handleGenericExecutionComplete(long currentTime, String entityId, String instructionId, EventType arrivalEventType) {
        Entity entity = entities.get(entityId);
        if (entity == null) return;

        // 1. 释放资源 & 上报完成
        physicsEngine.releaseAllByEntity(entityId);
        if (instructionId != null && !instructionId.startsWith("WAIT_")) {
            Instruction finishedInst = instructions.get(instructionId);
            if (finishedInst != null && finishedInst.isCompleted()) {
                taskService.reportTaskCompletion(instructionId, entityId);
            }
        }
        entity.setCurrentInstructionId(null);

        // 2. 索取新任务
        Instruction nextInstruction = taskService.askForNewTask(entity);

        if (nextInstruction != null) {
            if (!instructions.containsKey(nextInstruction.getInstructionId())) {
                instructions.put(nextInstruction.getInstructionId(), nextInstruction);
            }
            entity.setCurrentInstructionId(nextInstruction.getInstructionId());
            entity.setStatus(EntityStatus.MOVING);

            // 决策去向：不在起点先去起点，在起点去终点
            String currentPos = entity.getCurrentPosition();
            String targetPos = currentPos.equals(nextInstruction.getOrigin())
                    ? nextInstruction.getDestination()
                    : nextInstruction.getOrigin();

            scheduleMove(currentTime, entity, targetPos, nextInstruction.getInstructionId(), arrivalEventType);

        } else {
            entity.setStatus(EntityStatus.IDLE);
        }
    }

    /**
     * [修正] 调度移动
     * 1. 调用外部模块规划路径
     * 2. 尝试物理占用
     * 3. 失败则请求外部模块解决冲突
     */
    private void scheduleMove(long currentTime, Entity entity, String targetPos, String instructionId, EventType arrivalEventType) {
        // 如果就在原地，直接触发到达
        if (entity.getCurrentPosition().equals(targetPos)) {
            eventQueue.add(new SimEvent(currentTime, arrivalEventType, entity.getId(), instructionId, targetPos));
            return;
        }

        // 1. 外部路径规划
        List<String> pathNodes = taskService.findPath(entity.getCurrentPosition(), targetPos);
        if (pathNodes == null || pathNodes.isEmpty()) {
            System.err.println("错误: 无法规划路径 " + entity.getId() + " -> " + targetPos);
            entity.setStatus(EntityStatus.IDLE);
            return;
        }

        // 2. 转换为路段ID (用于物理引擎)
        List<String> segmentIds = new ArrayList<>();
        for (int i = 0; i < pathNodes.size() - 1; i++) {
            Segment seg = portMap.getSegmentBetween(pathNodes.get(i), pathNodes.get(i+1));
            if (seg != null) segmentIds.add(seg.getId());
        }

        try {
            // 3. 尝试占用物理资源
            physicsEngine.checkAndOccupyPath(segmentIds, entity.getId());

            // 4. 成功：计算时间并生成移动事件
            long moveTime = timeModule.estimateMovementTime(entity, entity.getCurrentPosition(), targetPos);
            eventQueue.add(new SimEvent(currentTime + moveTime,
                    arrivalEventType,
                    entity.getId(),
                    instructionId,
                    targetPos));

        } catch (RuntimeException e) {
            // 5. [修正] 冲突处理：由外部模块解决
            System.out.println("冲突检测: " + e.getMessage() + " -> 请求外部解决");

            Instruction currentInst = instructions.get(instructionId);
            Instruction fixInstruction = taskService.resolveCollision(entity, currentInst);

            if (fixInstruction != null && fixInstruction.getType() == InstructionType.WAIT) {
                // 处理等待指令
                entity.setStatus(EntityStatus.WAITING);
                // 这里的 5000 应该从 fixInstruction 获取，这里简化
                long waitTime = 5000;

                // 等待结束后，触发 ExecutionComplete，这将导致实体重新尝试索取任务或重试
                // 注意：这里使用 IT_EXECUTION_COMPLETE (或对应类型) 作为回调
                EventType completeType;
                switch(entity.getType()) {
                    case QC: completeType = EventType.QC_EXECUTION_COMPLETE; break;
                    case YC: completeType = EventType.YC_EXECUTION_COMPLETE; break;
                    default: completeType = EventType.IT_EXECUTION_COMPLETE; break;
                }

                eventQueue.add(new SimEvent(currentTime + waitTime, completeType, entity.getId(), null)); // null instructionId 标识这是临时等待结束
            } else {
                // 如果返回了新的移动路径等，可以在这里递归调用 scheduleMove，略
                entity.setStatus(EntityStatus.IDLE);
            }
        }
    }

    private void checkHandshakeAtPosition(long currentTime, Entity initiator, String position, Instruction instruction) {
        // 确定需要握手的另一方
        String partnerId = null;
        if (initiator.getType() == EntityType.IT) {
            if (position.contains("QUAY")) partnerId = instruction.getTargetQC();
            else if (position.contains("BAY") || position.contains("PARK")) partnerId = instruction.getTargetYC();
        } else {
            // 如果发起者是 QC/YC，寻找 IT
            partnerId = instruction.getTargetIT();
        }

        if (partnerId != null) {
            Entity partner = entities.get(partnerId);
            // 双方都在同一位置，且都在等待/空闲状态
            if (partner != null &&
                    partner.getCurrentPosition().equals(position) &&
                    (partner.getStatus() == EntityStatus.WAITING || partner.getStatus() == EntityStatus.IDLE)) {

                // 区分谁是 Crane 谁是 IT
                Entity crane = (initiator.getType() == EntityType.IT) ? partner : initiator;
                Entity it = (initiator.getType() == EntityType.IT) ? initiator : partner;

                startExecution(currentTime, crane, it, instruction);
            }
        }
    }

    private void startExecution(long currentTime, Entity crane, Entity it, Instruction instruction) {
        long craneTime = timeModule.estimateExecutionTime(crane, "EXECUTE");
        long itTime = timeModule.estimateExecutionTime(it, "OCCUPY");
        long totalTime = Math.max(craneTime, itTime);

        EventType craneEvent = (crane.getType() == EntityType.QC) ? EventType.QC_EXECUTION_COMPLETE : EventType.YC_EXECUTION_COMPLETE;

        eventQueue.add(new SimEvent(currentTime + totalTime, EventType.IT_EXECUTION_COMPLETE, it.getId(), instruction.getInstructionId()));
        eventQueue.add(new SimEvent(currentTime + craneTime, craneEvent, crane.getId(), instruction.getInstructionId()));

        crane.setStatus(EntityStatus.EXECUTING);
        it.setStatus(EntityStatus.EXECUTING);
        instruction.markInProgress();

        // 如果是任务终点，标记任务完成
        if (it.getCurrentPosition().equals(instruction.getDestination())) {
            instruction.markCompleted();
        }
    }
}