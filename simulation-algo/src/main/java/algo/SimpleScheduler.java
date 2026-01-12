package algo;

import entity.Entity;
import entity.EntityStatus;
import entity.EntityType;
import entity.IT;
import Instruction.Instruction;
import decision.ExternalTaskService;
import time.TimeEstimationModule;
import physics.PhysicsEngine;
import map.PortMap;
import map.Segment;
import event.EventType;
import event.SimEvent;

import java.util.*;

public class SimpleScheduler {
    private ExternalTaskService taskService;
    private TimeEstimationModule timeModule;
    private PhysicsEngine physicsEngine; // [修正] 持有物理引擎
    private PortMap portMap;             // 需要地图来获取路段信息

    private Map<String, Entity> entities;
    private Map<String, Instruction> instructions;
    private PriorityQueue<SimEvent> eventQueue;

    // [修正] 构造函数注入 PhysicsEngine 和 PortMap
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

    // --- 核心逻辑: 集卡 (IT) 任务调度修正 ---

    public void handleITExecutionComplete(long currentTime, String itId, String instructionId) {
        Entity it = entities.get(itId);
        if (it == null) return;

        // 1. 如果有旧任务，上报完成并释放旧路径资源
        if (instructionId != null) {
            Instruction finishedInst = instructions.get(instructionId);
            if (finishedInst != null && finishedInst.isCompleted()) {
                // 注意：这里简化处理，假设IT完成任务后就释放了所有路段
                physicsEngine.releaseAllByEntity(itId);
                taskService.reportTaskCompletion(instructionId, itId);
            }
        }
        it.setCurrentInstructionId(null);

        // 2. 索取新任务
        Instruction nextInstruction = taskService.askForNewTask(it);

        if (nextInstruction != null) {
            if (!instructions.containsKey(nextInstruction.getInstructionId())) {
                instructions.put(nextInstruction.getInstructionId(), nextInstruction);
            }
            it.setCurrentInstructionId(nextInstruction.getInstructionId());
            it.setStatus(EntityStatus.MOVING);

            String currentPos = it.getCurrentPosition();
            String originPos = nextInstruction.getOrigin();
            String targetPos;

            // [修正] 判断逻辑：不在起点则先去起点，在起点则去终点
            if (!currentPos.equals(originPos)) {
                // 情况A: 空载前往起点 (去取货)
                targetPos = originPos;
            } else {
                // 情况B: 已经在起点，前往终点 (送货)
                targetPos = nextInstruction.getDestination();
            }

            scheduleMove(currentTime, it, targetPos, nextInstruction.getInstructionId());

        } else {
            it.setStatus(EntityStatus.IDLE);
            physicsEngine.releaseAllByEntity(itId); // 空闲释放资源
        }
    }

    public void handleITArrival(long currentTime, String itId, String instructionId, String targetPosition) {
        Entity it = entities.get(itId);
        if (it == null) return;

        // 更新位置
        it.setCurrentPosition(targetPosition);

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        // [修正] 到达后的逻辑分支
        if (targetPosition.equals(instruction.getOrigin())) {
            // A. 到达了起点 -> 等待装货 (Handshake with YC/QC)
            it.setStatus(EntityStatus.WAITING);
            checkHandshakeAtPosition(currentTime, it, targetPosition, instruction);

        } else if (targetPosition.equals(instruction.getDestination())) {
            // B. 到达了终点 -> 等待卸货
            it.setStatus(EntityStatus.WAITING);
            checkHandshakeAtPosition(currentTime, it, targetPosition, instruction);
        }
    }

    // [新增] 统一的移动调度与物理检测
    private void scheduleMove(long currentTime, Entity entity, String targetPos, String instructionId) {
        String currentPos = entity.getCurrentPosition();

        // 1. 获取路径 (外部模块规划)
        List<String> pathNodes = portMap.findPath(currentPos, targetPos);

        // 2. 转换为路段ID列表
        List<String> segmentIds = new ArrayList<>();
        for (int i = 0; i < pathNodes.size() - 1; i++) {
            Segment seg = portMap.getSegmentBetween(pathNodes.get(i), pathNodes.get(i+1));
            if (seg != null) segmentIds.add(seg.getId());
        }

        try {
            // 3. 物理引擎检测与占用 (冲突则抛异常)
            physicsEngine.checkAndOccupyPath(segmentIds, entity.getId());

            // 4. 计算时间并生成事件
            long moveTime = timeModule.estimateMovementTime(entity, currentPos, targetPos);

            // 这里我们复用 ARRIVAL 事件。实际项目中可能需要 distinguish MOVE_START / MOVE_END
            eventQueue.add(new SimEvent(currentTime + moveTime,
                    EventType.IT_ARRIVAL, // 注意：这里简化了，QC/YC移动也用类似逻辑
                    entity.getId(),
                    instructionId,
                    targetPos));

        } catch (RuntimeException e) {
            System.err.println("移动失败: " + e.getMessage());
            // 简单策略：发生碰撞则原地等待 (实际可重试)
            entity.setStatus(EntityStatus.WAITING);
        }
    }

    // [重构] 提取握手逻辑
    private void checkHandshakeAtPosition(long currentTime, Entity it, String position, Instruction instruction) {
        // 检查是岸边还是堆场
        String targetCraneId = null;
        if (position.contains("QUAY")) targetCraneId = instruction.getTargetQC();
        else if (position.contains("BAY") || position.contains("PARK")) targetCraneId = instruction.getTargetYC();

        if (targetCraneId != null) {
            Entity crane = entities.get(targetCraneId);
            // 双方都在位且空闲/等待
            if (crane != null && crane.getCurrentPosition().equals(position) &&
                    (crane.getStatus() == EntityStatus.WAITING || crane.getStatus() == EntityStatus.IDLE)) {

                // 开始作业
                startExecution(currentTime, crane, it, instruction);
            }
        }
    }

    private void startExecution(long currentTime, Entity crane, Entity it, Instruction instruction) {
        long craneTime = timeModule.estimateExecutionTime(crane, "EXECUTE");
        long itTime = timeModule.estimateExecutionTime(it, "OCCUPY");
        long totalTime = Math.max(craneTime, itTime);

        // 调度完成事件
        // 判断是装货完成还是任务全部完成?
        // 简化：这里统一触发 EXECUTION_COMPLETE，由 handleXXXExecutionComplete 决定下一步
        EventType craneEvent = (crane.getType() == EntityType.QC) ? EventType.QC_EXECUTION_COMPLETE : EventType.YC_EXECUTION_COMPLETE;

        eventQueue.add(new SimEvent(currentTime + totalTime, EventType.IT_EXECUTION_COMPLETE, it.getId(), instruction.getInstructionId()));
        eventQueue.add(new SimEvent(currentTime + craneTime, craneEvent, crane.getId(), instruction.getInstructionId()));

        crane.setStatus(EntityStatus.EXECUTING);
        it.setStatus(EntityStatus.EXECUTING);
        instruction.markInProgress();

        //如果是任务终点，标记任务完成 (逻辑在handleITExecutionComplete中处理)
        if (it.getCurrentPosition().equals(instruction.getDestination())) {
            instruction.markCompleted();
        }
    }

    // (QC和YC的handle方法也需要适配注入的logic，为节省篇幅略，重点修正了IT逻辑)
    // 必须保留 handleQCExecutionComplete, handleQCArrival 等，逻辑与之前类似，但需调用 physicsEngine.releasePath
    public void handleQCExecutionComplete(long t, String id, String iId) {
        // ... 原有逻辑 ...
        // 记得处理 taskService.askForNewTask
    }
    public void handleQCArrival(long t, String id, String iId, String target) { /*...*/ }
    public void handleYCExecutionComplete(long t, String id, String iId) { /*...*/ }
    public void handleYCArrival(long t, String id, String iId, String target) { /*...*/ }
}