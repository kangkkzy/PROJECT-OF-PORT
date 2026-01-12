package algo;

import entity.Entity;
import entity.EntityStatus;
import Instruction.Instruction;
import Instruction.InstructionType;
import decision.ExternalTaskService;
import time.TimeEstimationModule;
import physics.PhysicsEngine;
import map.PortMap;
import event.EventType;
import event.SimEvent;

import java.util.*;

public class SimpleScheduler {
    private ExternalTaskService taskService;
    private TimeEstimationModule timeModule;
    private PhysicsEngine physicsEngine;
    private PortMap portMap; // 仅用于ID转换

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

        // 注册实体到物理引擎以便检测
        // physicsEngine.registerEntity(...)
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

    // ================= 核心逻辑：事件驱动执行 =================

    /**
     * 处理任务执行完成（通用）
     * 逻辑：上报完成 -> 询问下一条指令 -> 执行
     */
    public void handleExecutionComplete(long currentTime, String entityId, String instructionId) {
        Entity entity = entities.get(entityId);
        if (entity == null) return;

        // 1. 上报当前任务完成
        if (instructionId != null) {
            Instruction inst = instructions.get(instructionId);
            if (inst != null) {
                inst.markCompleted();
                taskService.reportTaskCompletion(instructionId, entityId);
            }
        }
        entity.setCurrentInstructionId(null);
        entity.setStatus(EntityStatus.IDLE);

        // 2. 向外部索取新指令 (PULL模式)
        Instruction nextInst = taskService.askForNewTask(entity);
        if (nextInst != null) {
            executeInstruction(currentTime, entity, nextInst);
        }
    }

    /**
     * 执行指令
     * 不做任何业务判断，只做物理模拟
     */
    private void executeInstruction(long currentTime, Entity entity, Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        entity.setCurrentInstructionId(instruction.getInstructionId());
        instruction.markInProgress();

        if (instruction.getType() == InstructionType.MOVE) {
            handleMoveInstruction(currentTime, entity, instruction);
        }
        else if (instruction.getType() == InstructionType.WAIT) {
            handleWaitInstruction(currentTime, entity, instruction);
        }
        else {
            // LOAD, UNLOAD, etc.
            handleOperationInstruction(currentTime, entity, instruction);
        }
    }

    private void handleMoveInstruction(long currentTime, Entity entity, Instruction instruction) {
        String from = entity.getCurrentPosition();
        String to = instruction.getDestination();

        // 1. 获取外部规划的路径 (Segment IDs)
        List<String> routeSegments = taskService.getRoute(from, to);

        // 2. 碰撞检测 (仅检测，不锁定)
        boolean hasCollision = false;
        if (routeSegments != null) {
            for (String segId : routeSegments) {
                if (physicsEngine.detectCollision(segId, entity.getId())) {
                    hasCollision = true;
                    taskService.reportCollision(entity.getId(), segId);
                    break;
                }
            }
        }

        if (hasCollision) {
            // 发生碰撞，实体进入等待状态，不再生成到达事件
            // 等待外部系统干预（例如发送新的 WAIT 指令或 REROUTE）
            entity.setStatus(EntityStatus.WAITING);
            return;
        }

        // 3. 计算时间并生成到达事件
        long duration = timeModule.estimateMovementTime(entity, routeSegments);
        entity.setStatus(EntityStatus.MOVING);

        EventType type = getArrivalType(entity);
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), instruction.getInstructionId(), to));
    }

    private void handleOperationInstruction(long currentTime, Entity entity, Instruction instruction) {
        // 直接执行操作，不检查握手 (Assume external system synced it)
        long duration = timeModule.estimateExecutionTime(entity, instruction.getType().name());

        entity.setStatus(EntityStatus.EXECUTING);

        EventType type = getCompleteType(entity);
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), instruction.getInstructionId()));
    }

    private void handleWaitInstruction(long currentTime, Entity entity, Instruction instruction) {
        // 假设等待指令隐含一个时长，或者默认 5秒
        long duration = 5000;
        entity.setStatus(EntityStatus.WAITING);

        EventType type = getCompleteType(entity);
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), instruction.getInstructionId()));
    }

    // ================= 事件回调适配器 =================

    public void handleITArrival(long currentTime, String id, String instrId, String pos) {
        Entity e = entities.get(id);
        if(e != null) e.setCurrentPosition(pos);
        // 到达后视为该指令（移动部分）执行结束，触发完成逻辑
        handleExecutionComplete(currentTime, id, instrId);
    }

    public void handleITExecutionComplete(long currentTime, String id, String instrId) {
        handleExecutionComplete(currentTime, id, instrId);
    }

    // QC 和 YC 同理
    public void handleQCArrival(long t, String id, String iId, String p) { handleITArrival(t, id, iId, p); }
    public void handleQCExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }
    public void handleYCArrival(long t, String id, String iId, String p) { handleITArrival(t, id, iId, p); }
    public void handleYCExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }

    private EventType getArrivalType(Entity e) {
        switch (e.getType()) {
            case QC: return EventType.QC_ARRIVAL;
            case YC: return EventType.YC_ARRIVAL;
            default: return EventType.IT_ARRIVAL;
        }
    }

    private EventType getCompleteType(Entity e) {
        switch (e.getType()) {
            case QC: return EventType.QC_EXECUTION_COMPLETE;
            case YC: return EventType.YC_EXECUTION_COMPLETE;
            default: return EventType.IT_EXECUTION_COMPLETE;
        }
    }
}