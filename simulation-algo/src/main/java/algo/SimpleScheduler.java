package algo;

import entity.Entity;
import entity.EntityStatus;
import entity.EntityType;
import Instruction.Instruction;
import Instruction.InstructionType;
import decision.ExternalTaskService;
import decision.TaskGenerator; // 新增引用
import map.GridMap;
import time.TimeEstimationModule;
import physics.PhysicsEngine;
import event.EventType;
import event.SimEvent;

import java.util.*;

public class SimpleScheduler {
    private ExternalTaskService taskService;
    private TimeEstimationModule timeModule;
    private PhysicsEngine physicsEngine;
    private GridMap gridMap;
    private TaskGenerator taskGenerator; // 新增：持有外部生成器

    private Map<String, Entity> entities;
    private Map<String, Instruction> instructions;
    private PriorityQueue<SimEvent> eventQueue;

    // 任务生成心跳间隔 (ms)
    private static final long GENERATION_INTERVAL = 5000;

    // 修改构造函数，注入 TaskGenerator
    public SimpleScheduler(ExternalTaskService taskService,
                           TimeEstimationModule timeModule,
                           PhysicsEngine physicsEngine,
                           GridMap gridMap,
                           TaskGenerator taskGenerator) {
        this.taskService = taskService;
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
        String gridPos = gridMap.getNodePosition(entity.getCurrentPosition());
        if (gridPos != null) entity.setCurrentPosition(gridPos);
        physicsEngine.lockResources(entity.getId(), Collections.singletonList(entity.getCurrentPosition()));
    }

    public void addInstruction(Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        taskService.submitTask(instruction);
    }

    public SimEvent getNextEvent() { return eventQueue.poll(); }
    public boolean hasPendingEvents() { return !eventQueue.isEmpty(); }

    public void init() {
        System.out.println("正在初始化仿真事件(Grid模式)...");

        // 1. 尝试分配初始静态任务
        for (Entity entity : entities.values()) {
            if (entity.getStatus() == EntityStatus.IDLE) {
                Instruction inst = taskService.askForNewTask(entity);
                if (inst != null) executeInstruction(0, entity, inst);
            }
        }

        // 2. 启动动态任务生成器 (事件驱动)
        if (taskGenerator != null) {
            System.out.println(">> 启动动态任务生成器...");
            eventQueue.add(new SimEvent(1000, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    // --- 核心：处理任务生成事件 ---
    public void handleTaskGeneration(long currentTime) {
        if (taskGenerator != null) {
            Instruction task = taskGenerator.generate(currentTime);
            if (task != null) {
                System.out.println(">>> [系统生成] 动态生成任务: " + task.getInstructionId() +
                        " (" + task.getOrigin() + "->" + task.getDestination() + ")");
                addInstruction(task);

                // 关键：生成新任务后，主动唤醒空闲的集卡去领任务
                for (Entity entity : entities.values()) {
                    if (entity.getType() == EntityType.IT && entity.getStatus() == EntityStatus.IDLE) {
                        Instruction next = taskService.askForNewTask(entity);
                        if (next != null) executeInstruction(currentTime, entity, next);
                    }
                }
            }
            // 预约下一次生成，形成无限循环
            eventQueue.add(new SimEvent(currentTime + GENERATION_INTERVAL, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    public void handleExecutionComplete(long currentTime, String entityId, String instructionId) {
        Entity entity = entities.get(entityId);
        if (entity == null) return;

        if (instructionId != null && !instructionId.startsWith("EMERGENCY") && !instructionId.startsWith("WAIT")) {
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

        Instruction nextInst = taskService.askForNewTask(entity);
        if (nextInst != null) executeInstruction(currentTime, entity, nextInst);
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
        List<String> routeCells = taskService.getRoute(instruction.getOrigin(), instruction.getDestination());
        if (routeCells == null || routeCells.isEmpty()) {
            handleITArrival(currentTime, entity.getId(), instruction.getInstructionId(), entity.getCurrentPosition());
            return;
        }
        entity.setRemainingPath(routeCells);
        processNextMovementStep(currentTime, entity);
    }

    private void processNextMovementStep(long currentTime, Entity entity) {
        if (!entity.hasRemainingPath()) {
            if (entity.getType() == EntityType.IT)
                handleITArrival(currentTime, entity.getId(), entity.getCurrentInstructionId(), entity.getCurrentPosition());
            else
                handleExecutionComplete(currentTime, entity.getId(), entity.getCurrentInstructionId());
            return;
        }

        Instruction interruptInst = taskService.askForInterruption(entity);
        if (interruptInst != null) {
            entity.setRemainingPath(null);
            executeInstruction(currentTime, entity, interruptInst);
            return;
        }

        String nextPosKey = entity.getRemainingPath().get(0);

        boolean isAllowedOverlap = false;
        if (physicsEngine.detectCollision(nextPosKey, entity.getId())) {
            String occupierId = physicsEngine.getOccupier(nextPosKey);
            Instruction currentInst = instructions.get(entity.getCurrentInstructionId());
            boolean isTarget = false;
            if (currentInst != null && occupierId != null) {
                if (occupierId.equals(currentInst.getTargetQC()) || occupierId.equals(currentInst.getTargetYC())) isTarget = true;
            }
            if (isTarget && entity.getRemainingPath().size() == 1) isAllowedOverlap = true;
            else {
                Instruction recovery = taskService.askForCollisionSolution(entity.getId(), nextPosKey);
                if (recovery != null) executeInstruction(currentTime, entity, recovery);
                else entity.setStatus(EntityStatus.WAITING);
                return;
            }
        }

        if (!isAllowedOverlap) physicsEngine.lockResources(entity.getId(), Collections.singletonList(nextPosKey));

        long duration = timeModule.estimateMovementTime(entity, Collections.singletonList(nextPosKey));
        entity.setStatus(EntityStatus.MOVING);

        EventType type = (entity.getRemainingPath().size() > 1) ? EventType.MOVE_STEP : getArrivalType(entity);
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), entity.getCurrentInstructionId(), nextPosKey));
    }

    public void handleStepArrival(long currentTime, String entityId, String instructionId, String targetPosKey) {
        handleCellArrivalCommon(entityId, targetPosKey);
        Entity entity = entities.get(entityId);
        if (entity != null) processNextMovementStep(currentTime, entity);
    }

    public void handleITArrival(long currentTime, String entityId, String instructionId, String targetPosKey) {
        Entity it = entities.get(entityId);
        if (it == null) return;
        handleCellArrivalCommon(entityId, targetPosKey);
        Instruction inst = instructions.get(instructionId);
        if (inst != null && inst.getType() == InstructionType.LOAD_TO_SHIP) tryStartInteraction(currentTime, it, inst);
        else handleExecutionComplete(currentTime, entityId, instructionId);
    }

    public void handleQCArrival(long t, String id, String iId, String p) { handleCellArrivalCommon(id, p); handleExecutionComplete(t, id, iId); }
    public void handleYCArrival(long t, String id, String iId, String p) { handleCellArrivalCommon(id, p); handleExecutionComplete(t, id, iId); }

    private void handleCellArrivalCommon(String entityId, String targetPosKey) {
        Entity entity = entities.get(entityId);
        String oldPos = entity.getCurrentPosition();
        if (oldPos != null && !oldPos.equals(targetPosKey)) physicsEngine.unlockSingleResource(entityId, oldPos);
        entity.setCurrentPosition(targetPosKey);
        entity.popNextStep();
    }

    private void tryStartInteraction(long currentTime, Entity it, Instruction inst) {
        String targetQcId = inst.getTargetQC();
        if (targetQcId != null) {
            Entity qc = entities.get(targetQcId);
            if (qc != null && qc.getCurrentPosition().equals(it.getCurrentPosition())) {
                if (qc.getStatus() == EntityStatus.IDLE || qc.getStatus() == EntityStatus.WAITING) {
                    System.out.println(">>> [交互开始] 集卡 " + it.getId() + " 与 岸桥 " + qc.getId() + " 开始作业");
                    qc.setStatus(EntityStatus.EXECUTING);
                    it.setStatus(EntityStatus.EXECUTING);
                    qc.setCurrentInstructionId(inst.getInstructionId());
                    long duration = inst.getExpectedDuration() > 0 ? inst.getExpectedDuration() : 30000;
                    eventQueue.add(new SimEvent(currentTime + duration, EventType.QC_EXECUTION_COMPLETE, qc.getId(), inst.getInstructionId()));
                    eventQueue.add(new SimEvent(currentTime + duration, EventType.IT_EXECUTION_COMPLETE, it.getId(), inst.getInstructionId()));
                } else {
                    System.out.println(">>> [交互等待] 岸桥 " + qc.getId() + " 忙碌，集卡 " + it.getId() + " 进入等待");
                    it.setStatus(EntityStatus.WAITING);
                }
                return;
            }
        }
        handleExecutionComplete(currentTime, it.getId(), inst.getInstructionId());
    }

    public void handleITExecutionComplete(long t, String id, String iId) { System.out.println(">>> [完成] 集卡 " + id + " 作业完成"); handleExecutionComplete(t, id, iId); }
    public void handleQCExecutionComplete(long t, String id, String iId) { System.out.println(">>> [完成] 岸桥 " + id + " 作业完成"); handleExecutionComplete(t, id, iId); }
    public void handleYCExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }

    private void handleOperationInstruction(long currentTime, Entity entity, Instruction instruction) {
        long duration = instruction.getExpectedDuration();
        if (duration <= 0) duration = 1000;
        entity.setStatus(EntityStatus.EXECUTING);
        eventQueue.add(new SimEvent(currentTime + duration, getCompleteType(entity), entity.getId(), instruction.getInstructionId()));
    }

    private void handleWaitInstruction(long currentTime, Entity entity, Instruction instruction) {
        long duration = instruction.getExpectedDuration();
        entity.setStatus(EntityStatus.WAITING);
        eventQueue.add(new SimEvent(currentTime + duration, getCompleteType(entity), entity.getId(), instruction.getInstructionId()));
    }

    private EventType getArrivalType(Entity e) { switch(e.getType()){case QC:return EventType.QC_ARRIVAL;case YC:return EventType.YC_ARRIVAL;default:return EventType.IT_ARRIVAL;}}
    private EventType getCompleteType(Entity e) { switch(e.getType()){case QC:return EventType.QC_EXECUTION_COMPLETE;case YC:return EventType.YC_EXECUTION_COMPLETE;default:return EventType.IT_EXECUTION_COMPLETE;}}
}