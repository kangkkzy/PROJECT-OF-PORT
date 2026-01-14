package algo;

import entity.Entity;
import entity.EntityStatus;
import Instruction.Instruction;
import Instruction.InstructionType;
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
    private PhysicsEngine physicsEngine;
    private PortMap portMap;

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
        physicsEngine.registerEntity(entity);
    }

    public void addInstruction(Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        taskService.submitTask(instruction);
    }

    public SimEvent getNextEvent() { return eventQueue.poll(); }

    /**
     * 【新增】仿真初始化：冷启动所有设备
     * 遍历所有实体，如果处于空闲状态，尝试索取任务并生成第一个事件。
     */
    public void init() {
        System.out.println("正在初始化仿真事件...");
        long initialTime = 0; // 仿真起始时间

        for (Entity entity : entities.values()) {
            // 只有空闲的设备才需要索取新任务
            // (如果在 entities.json 中定义了初始携带指令，这里需要额外处理，目前假设初始都为空闲)
            if (entity.getStatus() == EntityStatus.IDLE) {
                Instruction inst = taskService.askForNewTask(entity);
                if (inst != null) {
                    System.out.println(String.format("初始化: 设备 %s 获取首个任务 %s", entity.getId(), inst.getInstructionId()));
                    executeInstruction(initialTime, entity, inst);
                }
            }
        }

        if (eventQueue.isEmpty()) {
            System.err.println("警告: 初始化后事件队列为空，仿真可能立即结束！(请检查是否有任务匹配设备)");
        }
    }

    public void handleExecutionComplete(long currentTime, String entityId, String instructionId) {
        physicsEngine.unlockResources(entityId);

        Entity entity = entities.get(entityId);
        if (entity == null) return;

        // 只有正常的业务指令完成才上报
        if (instructionId != null && !instructionId.startsWith("EMERGENCY_PAUSE") && !instructionId.startsWith("WAIT_COLLISION")) {
            if (instructionId.equals(entity.getCurrentInstructionId())) {
                Instruction inst = instructions.get(instructionId);
                if (inst != null) {
                    inst.markCompleted();
                    taskService.reportTaskCompletion(instructionId, entityId);
                }
            }
        }

        entity.setCurrentInstructionId(null);
        entity.setStatus(EntityStatus.IDLE);
        entity.setRemainingPath(null);

        // 消费完成后，逻辑产生新事件 (索取新任务)
        Instruction nextInst = taskService.askForNewTask(entity);
        if (nextInst != null) {
            executeInstruction(currentTime, entity, nextInst);
        }
    }

    private void executeInstruction(long currentTime, Entity entity, Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        entity.setCurrentInstructionId(instruction.getInstructionId());
        instruction.markInProgress();

        if (instruction.getType() == InstructionType.MOVE) {
            startMoveInstruction(currentTime, entity, instruction);
        }
        else if (instruction.getType() == InstructionType.WAIT) {
            handleWaitInstruction(currentTime, entity, instruction);
        }
        else {
            handleOperationInstruction(currentTime, entity, instruction);
        }
    }

    private void startMoveInstruction(long currentTime, Entity entity, Instruction instruction) {
        String from = entity.getCurrentPosition();
        String to = instruction.getDestination();

        List<String> routeSegments = taskService.getRoute(from, to);
        // 如果无法寻路或已经在原点，直接完成
        if (routeSegments == null || routeSegments.isEmpty()) {
            handleExecutionComplete(currentTime, entity.getId(), instruction.getInstructionId());
            return;
        }

        entity.setRemainingPath(routeSegments);
        processNextMovementStep(currentTime, entity);
    }

    // 处理移动的每一步 (分段逻辑)
    private void processNextMovementStep(long currentTime, Entity entity) {
        // 1. 结束检查
        if (!entity.hasRemainingPath()) {
            handleExecutionComplete(currentTime, entity.getId(), entity.getCurrentInstructionId());
            return;
        }

        // 2. 中断检查 (消费事件时的逻辑分支)
        Instruction interruptInst = taskService.askForInterruption(entity);
        if (interruptInst != null) {
            System.out.println("!!! 实体 " + entity.getId() + " 响应中断，切换指令");
            physicsEngine.unlockResources(entity.getId());
            entity.setRemainingPath(null);
            executeInstruction(currentTime, entity, interruptInst);
            return;
        }

        // 3. 正常执行下一步
        String nextSegmentId = entity.getRemainingPath().get(0);

        if (physicsEngine.detectCollision(nextSegmentId, entity.getId())) {
            Instruction recovery = taskService.askForCollisionSolution(entity.getId(), nextSegmentId);
            if (recovery != null) {
                executeInstruction(currentTime, entity, recovery);
            } else {
                entity.setStatus(EntityStatus.WAITING);
            }
            return;
        }

        physicsEngine.lockSegments(entity.getId(), Collections.singletonList(nextSegmentId));
        long duration = timeModule.estimateMovementTime(entity, Collections.singletonList(nextSegmentId));

        // 模拟执行耗时 (仅用于观察效果，正式跑分时应去掉)
        try { Thread.sleep(50); } catch (Exception e) {}

        Segment seg = portMap.getSegment(nextSegmentId);
        String targetNodeId = (seg.getFromNodeId().equals(entity.getCurrentPosition())) ? seg.getToNodeId() : seg.getFromNodeId();

        entity.setStatus(EntityStatus.MOVING);
        EventType type = getArrivalType(entity);

        // 产生新事件：到达下一段节点
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), entity.getCurrentInstructionId(), targetNodeId));
    }

    public void handleITArrival(long t, String id, String iId, String p) { handleSegmentArrival(t, id, iId, p); }
    public void handleQCArrival(long t, String id, String iId, String p) { handleSegmentArrival(t, id, iId, p); }
    public void handleYCArrival(long t, String id, String iId, String p) { handleSegmentArrival(t, id, iId, p); }

    private void handleSegmentArrival(long currentTime, String entityId, String instructionId, String targetPosition) {
        Entity entity = entities.get(entityId);
        if (entity == null) return;
        if (instructionId != null && !instructionId.equals(entity.getCurrentInstructionId())) return;

        entity.setCurrentPosition(targetPosition);
        physicsEngine.unlockResources(entityId);
        entity.popNextSegment();

        processNextMovementStep(currentTime, entity);
    }

    private void handleOperationInstruction(long currentTime, Entity entity, Instruction instruction) {
        long duration = instruction.getExpectedDuration();
        entity.setStatus(EntityStatus.EXECUTING);
        EventType type = getCompleteType(entity);
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), instruction.getInstructionId()));
    }

    private void handleWaitInstruction(long currentTime, Entity entity, Instruction instruction) {
        long duration = instruction.getExpectedDuration();
        entity.setStatus(EntityStatus.WAITING);
        EventType type = getCompleteType(entity);
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), instruction.getInstructionId()));
    }

    public void handleITExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }
    public void handleQCExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }
    public void handleYCExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }

    private EventType getArrivalType(Entity e) { switch(e.getType()){case QC:return EventType.QC_ARRIVAL;case YC:return EventType.YC_ARRIVAL;default:return EventType.IT_ARRIVAL;}}
    private EventType getCompleteType(Entity e) { switch(e.getType()){case QC:return EventType.QC_EXECUTION_COMPLETE;case YC:return EventType.YC_EXECUTION_COMPLETE;default:return EventType.IT_EXECUTION_COMPLETE;}}
}