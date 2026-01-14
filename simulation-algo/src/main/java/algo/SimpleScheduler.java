package algo;

import entity.Entity;
import entity.EntityStatus;
import entity.EntityType;
import Instruction.Instruction;
import Instruction.InstructionType;
import decision.TaskAllocator;   // 新接口
import decision.TrafficController; // 新接口
import decision.RoutePlanner;
import decision.TaskGenerator;
import map.GridMap;
import map.Location; // 新类
import physics.PhysicsEngine;
import event.EventType;
import event.SimEvent;
import time.TimeEstimationModule;

import java.util.*;

public class SimpleScheduler {
    // 替换原有的 ExternalTaskService 和 TaskDispatcher
    private final TaskAllocator taskAllocator;
    private final TrafficController trafficController;
    private final RoutePlanner routePlanner;

    private final TimeEstimationModule timeModule;
    private final PhysicsEngine physicsEngine;
    private final GridMap gridMap;
    private final TaskGenerator taskGenerator;

    private Map<String, Entity> entities;
    private Map<String, Instruction> instructions;
    private PriorityQueue<SimEvent> eventQueue;

    // 构造函数注入所有依赖
    public SimpleScheduler(TaskAllocator taskAllocator,
                           TrafficController trafficController,
                           RoutePlanner routePlanner,
                           TimeEstimationModule timeModule,
                           PhysicsEngine physicsEngine,
                           GridMap gridMap,
                           TaskGenerator taskGenerator) {
        this.taskAllocator = taskAllocator;
        this.trafficController = trafficController;
        this.routePlanner = routePlanner;
        this.timeModule = timeModule;
        this.physicsEngine = physicsEngine;
        this.gridMap = gridMap;
        this.taskGenerator = taskGenerator;

        this.entities = new HashMap<>();
        this.instructions = new HashMap<>();
        this.eventQueue = new PriorityQueue<>();
    }

    public void registerEntity(Entity entity) {
        entities.put(entity.getId(), entity);
        // 初始化 Location: 从 NodeID ("BAY_A01") -> Location(10, 20)
        Location startLoc = gridMap.getNodeLocation(entity.getInitialNodeId());
        if (startLoc != null) {
            entity.setCurrentLocation(startLoc);
            // 锁定初始位置
            physicsEngine.lockResources(entity.getId(), Collections.singletonList(startLoc));
        } else {
            System.err.println("警告: 实体 " + entity.getId() + " 的初始位置无效: " + entity.getInitialNodeId());
        }
    }

    public void addInstruction(Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        taskAllocator.onNewTaskSubmitted(instruction);
    }

    public SimEvent getNextEvent() { return eventQueue.poll(); }
    public boolean hasPendingEvents() { return !eventQueue.isEmpty(); }

    public void init() {
        // 初始分配
        for (Entity entity : entities.values()) {
            if (entity.getStatus() == EntityStatus.IDLE) {
                Instruction inst = taskAllocator.assignTask(entity);
                if (inst != null) executeInstruction(0, entity, inst);
            }
        }
        // 启动动态生成
        if (taskGenerator != null) {
            eventQueue.add(new SimEvent(1000, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    public void handleTaskGeneration(long currentTime) {
        if (taskGenerator != null) {
            Instruction task = taskGenerator.generate(currentTime);
            if (task != null) {
                addInstruction(task);
                // 唤醒空闲集卡
                for (Entity entity : entities.values()) {
                    if (entity.getType() == EntityType.IT && entity.getStatus() == EntityStatus.IDLE) {
                        Instruction next = taskAllocator.assignTask(entity);
                        if (next != null) executeInstruction(currentTime, entity, next);
                    }
                }
            }
            eventQueue.add(new SimEvent(currentTime + 5000, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    private void executeInstruction(long currentTime, Entity entity, Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        entity.setCurrentInstructionId(instruction.getInstructionId());
        instruction.markInProgress();

        if (instruction.getType() == InstructionType.MOVE || instruction.getType() == InstructionType.LOAD_TO_SHIP) {
            startMoveInstruction(currentTime, entity, instruction);
        } else if (instruction.getType() == InstructionType.WAIT) {
            handleWaitInstruction(currentTime, entity, instruction);
        } else {
            handleOperationInstruction(currentTime, entity, instruction);
        }
    }

    private void startMoveInstruction(long currentTime, Entity entity, Instruction instruction) {
        // 核心路径规划逻辑升级
        List<Location> path = routePlanner.searchRoute(instruction.getOrigin(), instruction.getDestination());

        if (path == null || path.isEmpty()) {
            // 已经在目的地或无法到达
            handleStepArrival(currentTime, entity.getId(), instruction.getInstructionId(), null);
            return;
        }
        entity.setRemainingPath(path);
        processNextMovementStep(currentTime, entity);
    }

    private void processNextMovementStep(long currentTime, Entity entity) {
        if (!entity.hasRemainingPath()) {
            handleArrival(currentTime, entity);
            return;
        }

        // 交通控制检查 (Emergency Stop)
        Instruction interrupt = trafficController.checkInterruption(entity);
        if (interrupt != null) {
            entity.setRemainingPath(null);
            executeInstruction(currentTime, entity, interrupt);
            return;
        }

        // 获取下一步坐标
        Location nextLoc = entity.getRemainingPath().get(0);

        // 碰撞检测
        boolean isAllowedOverlap = false;
        if (physicsEngine.detectCollision(nextLoc, entity.getId())) {
            String occupierId = physicsEngine.getOccupier(nextLoc);
            // 简单逻辑：如果是目标作业对象（如 QC），允许重叠
            Instruction currentInst = instructions.get(entity.getCurrentInstructionId());
            boolean isTarget = false;
            if (currentInst != null && occupierId != null) {
                if (occupierId.equals(currentInst.getTargetQC()) || occupierId.equals(currentInst.getTargetYC())) isTarget = true;
            }

            if (isTarget && entity.getRemainingPath().size() == 1) {
                isAllowedOverlap = true;
            } else {
                // 请求避障方案
                Instruction recovery = trafficController.resolveCollision(entity, occupierId);
                if (recovery != null) executeInstruction(currentTime, entity, recovery);
                else entity.setStatus(EntityStatus.WAITING);
                return;
            }
        }

        if (!isAllowedOverlap) {
            physicsEngine.lockResources(entity.getId(), Collections.singletonList(nextLoc));
        }

        long duration = timeModule.estimateMovementTime(entity, Collections.singletonList(nextLoc));
        entity.setStatus(EntityStatus.MOVING);

        EventType type = (entity.getRemainingPath().size() > 1) ? EventType.MOVE_STEP : getArrivalType(entity);
        // Event 暂时还存 String, 转换一下
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), entity.getCurrentInstructionId(), nextLoc.toKey()));
    }

    // 到达某个格子的处理
    public void handleStepArrival(long currentTime, String entityId, String instructionId, String targetPosKey) {
        Entity entity = entities.get(entityId);
        if (entity == null) return;

        Location oldLoc = entity.getCurrentLocation();
        Location targetLoc = targetPosKey != null ? Location.parse(targetPosKey) : entity.getCurrentLocation();

        // 释放旧格子
        if (oldLoc != null && !oldLoc.equals(targetLoc)) {
            physicsEngine.unlockSingleResource(entityId, oldLoc);
        }

        entity.setCurrentLocation(targetLoc);
        entity.popNextStep(); // 从路径列表中移除已到达的一步

        // 继续走下一步
        processNextMovementStep(currentTime, entity);
    }

    // 最终到达目的地处理
    private void handleArrival(long currentTime, Entity entity) {
        Instruction inst = instructions.get(entity.getCurrentInstructionId());
        if (inst != null && inst.getType() == InstructionType.LOAD_TO_SHIP) {
            tryStartInteraction(currentTime, entity, inst);
        } else {
            handleExecutionComplete(currentTime, entity.getId(), entity.getCurrentInstructionId());
        }
    }

    // ... (handleExecutionComplete, tryStartInteraction, handleWaitInstruction 等逻辑与原代码逻辑基本一致，只需确保不直接操作字符串坐标) ...

    public void handleExecutionComplete(long currentTime, String entityId, String instructionId) {
        Entity entity = entities.get(entityId);
        if (entity == null) return;

        // 报告完成
        if (instructionId != null && !instructionId.startsWith("WAIT")) {
            Instruction inst = instructions.get(instructionId);
            if (inst != null) {
                inst.markCompleted();
                taskAllocator.onTaskCompleted(instructionId);
            }
        }

        entity.setCurrentInstructionId(null);
        entity.setStatus(EntityStatus.IDLE);
        entity.setRemainingPath(null);

        // 请求新任务
        Instruction nextInst = taskAllocator.assignTask(entity);
        if (nextInst != null) executeInstruction(currentTime, entity, nextInst);
    }

    // ... (Helper methods getArrivalType etc. unchanged) ...
    private EventType getArrivalType(Entity e) { switch(e.getType()){case QC:return EventType.QC_ARRIVAL;case YC:return EventType.YC_ARRIVAL;default:return EventType.IT_ARRIVAL;}}

    // ... (交互逻辑 tryStartInteraction 略，检查 qc.getCurrentLocation().equals(it.getCurrentLocation()) 即可) ...
    private void tryStartInteraction(long currentTime, Entity it, Instruction inst) {
        String targetQcId = inst.getTargetQC();
        if (targetQcId != null) {
            Entity qc = entities.get(targetQcId);
            // Location 对象比较
            if (qc != null && qc.getCurrentLocation().equals(it.getCurrentLocation())) {
                // ... (交互逻辑不变) ...
                qc.setStatus(EntityStatus.EXECUTING);
                it.setStatus(EntityStatus.EXECUTING);
                long duration = inst.getExpectedDuration() > 0 ? inst.getExpectedDuration() : 30000;
                eventQueue.add(new SimEvent(currentTime + duration, EventType.QC_EXECUTION_COMPLETE, qc.getId(), inst.getInstructionId()));
                eventQueue.add(new SimEvent(currentTime + duration, EventType.IT_EXECUTION_COMPLETE, it.getId(), inst.getInstructionId()));
                return;
            }
        }
        handleExecutionComplete(currentTime, it.getId(), inst.getInstructionId());
    }

    private void handleOperationInstruction(long t, Entity e, Instruction i) {
        long d = i.getExpectedDuration() > 0 ? i.getExpectedDuration() : 1000;
        e.setStatus(EntityStatus.EXECUTING);
        eventQueue.add(new SimEvent(t + d, getCompleteType(e), e.getId(), i.getInstructionId()));
    }
    private void handleWaitInstruction(long t, Entity e, Instruction i) {
        long d = i.getExpectedDuration();
        e.setStatus(EntityStatus.WAITING);
        eventQueue.add(new SimEvent(t + d, getCompleteType(e), e.getId(), i.getInstructionId()));
    }
    private EventType getCompleteType(Entity e) { switch(e.getType()){case QC:return EventType.QC_EXECUTION_COMPLETE;case YC:return EventType.YC_EXECUTION_COMPLETE;default:return EventType.IT_EXECUTION_COMPLETE;}}

    // 为了兼容 Event 系统，需要这些桥接方法 (Event 回调入口)
    public void handleQCExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }
    public void handleYCExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }
    public void handleITExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }

    public void handleQCArrival(long t, String id, String iId, String p) { handleStepArrival(t, id, iId, p); }
    public void handleYCArrival(long t, String id, String iId, String p) { handleStepArrival(t, id, iId, p); }
    public void handleITArrival(long t, String id, String iId, String p) { handleStepArrival(t, id, iId, p); }
}