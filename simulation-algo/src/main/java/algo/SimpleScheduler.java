package algo;

import decision.*;
import entity.*;
import Instruction.*;
import map.GridMap;
import map.Location;
import physics.PhysicsEngine;
import event.EventType;
import event.SimEvent;
import time.TimeEstimationModule;
import java.util.*;

public class SimpleScheduler {
    private final TaskAllocator taskAllocator;
    private final TrafficController trafficController;
    private final RoutePlanner routePlanner;
    private final TaskGenerator taskGenerator;
    private final TimeEstimationModule timeModule;
    private final PhysicsEngine physicsEngine;
    private final GridMap gridMap;

    private final Map<String, Entity> entities = new HashMap<>();
    private final Map<String, Instruction> instructions = new HashMap<>();
    private final Queue<SimEvent> pendingEvents = new LinkedList<>();

    public SimpleScheduler(TaskAllocator taskAllocator, TrafficController trafficController,
                           RoutePlanner routePlanner, TimeEstimationModule timeModule,
                           PhysicsEngine physicsEngine, GridMap gridMap, TaskGenerator taskGenerator) {
        this.taskAllocator = taskAllocator;
        this.trafficController = trafficController;
        this.routePlanner = routePlanner;
        this.timeModule = timeModule;
        this.physicsEngine = physicsEngine;
        this.gridMap = gridMap;
        this.taskGenerator = taskGenerator;
    }

    public void init() {
        for (Entity entity : entities.values()) {
            Instruction inst = taskAllocator.assignTask(entity);
            if (inst != null) {
                instructions.put(inst.getInstructionId(), inst);
                entity.setCurrentInstructionId(inst.getInstructionId());
                evaluateStateAndAddEvents(0, entity, inst);
            } else {
                entity.setStatus(EntityStatus.IDLE);
            }
        }
        if (taskGenerator != null) {
            pendingEvents.add(new SimEvent(0, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    private void evaluateStateAndAddEvents(long currentTime, Entity entity, Instruction inst) {
        Location currentLoc = entity.getCurrentLocation();
        Location targetLoc = determineTargetLocation(entity, inst);

        if (Objects.equals(currentLoc, targetLoc)) {
            handleArrivalLogic(currentTime, entity, inst);
        } else {
            moveToTarget(currentTime, entity, targetLoc, inst);
        }
    }

    private Location determineTargetLocation(Entity entity, Instruction inst) {
        if (entity.getType() == EntityType.IT) {
            IT it = (IT) entity;
            if (!it.isLoaded() && inst.getType() == InstructionType.LOAD_TO_SHIP) {
                return gridMap.getNodeLocation(inst.getOrigin());
            } else {
                return gridMap.getNodeLocation(inst.getDestination());
            }
        }
        if (entity.getType() == EntityType.QC) return gridMap.getNodeLocation(inst.getDestination());
        if (entity.getType() == EntityType.YC) return gridMap.getNodeLocation(inst.getOrigin());
        return entity.getCurrentLocation();
    }

    // --- 事件消费逻辑 ---

    public void handleQCExecutionComplete(long currentTime, String entityId, String instructionId) {
        Entity qc = entities.get(entityId);
        Instruction completedInst = instructions.get(instructionId);
        if (completedInst != null) {
            completedInst.markCompleted();
            taskAllocator.onTaskCompleted(instructionId);
        }

        Instruction nextInst = taskAllocator.assignTask(qc);
        if (nextInst == null) {
            qc.setStatus(EntityStatus.IDLE);
            return;
        }

        instructions.put(nextInst.getInstructionId(), nextInst);
        qc.setCurrentInstructionId(nextInst.getInstructionId());

        Location qcLoc = qc.getCurrentLocation();
        Location nextTarget = gridMap.getNodeLocation(nextInst.getDestination());

        if (Objects.equals(qcLoc, nextTarget)) {
            Entity it = findEntity(nextInst.getTargetIT());
            if (checkWait(it, qcLoc)) scheduleJointExecution(currentTime, qc, it, nextInst);
            else qc.setStatus(EntityStatus.WAITING);
        } else {
            moveToTarget(currentTime, qc, nextTarget, nextInst);
        }
    }

    public void handleQCArrival(long currentTime, String entityId, String instructionId, String targetPosKey) {
        Entity qc = entities.get(entityId);
        qc.setStatus(EntityStatus.IDLE);
        Instruction inst = instructions.get(instructionId);

        Entity it = findEntity(inst.getTargetIT());
        if (checkWait(it, Location.parse(targetPosKey))) scheduleJointExecution(currentTime, qc, it, inst);
        else qc.setStatus(EntityStatus.WAITING);
    }

    public void handleYCExecutionComplete(long currentTime, String entityId, String instructionId) {
        Entity yc = entities.get(entityId);
        Instruction nextInst = taskAllocator.assignTask(yc);
        if (nextInst == null) {
            yc.setStatus(EntityStatus.IDLE);
            return;
        }

        instructions.put(nextInst.getInstructionId(), nextInst);
        yc.setCurrentInstructionId(nextInst.getInstructionId());

        Location ycLoc = yc.getCurrentLocation();
        Location nextTarget = gridMap.getNodeLocation(nextInst.getOrigin());

        if (Objects.equals(ycLoc, nextTarget)) {
            Entity it = findEntity(nextInst.getTargetIT());
            if (checkWait(it, ycLoc)) scheduleJointExecution(currentTime, yc, it, nextInst);
            else yc.setStatus(EntityStatus.WAITING);
        } else {
            moveToTarget(currentTime, yc, nextTarget, nextInst);
        }
    }

    public void handleYCArrival(long currentTime, String entityId, String instructionId, String targetPosKey) {
        Entity yc = entities.get(entityId);
        yc.setStatus(EntityStatus.IDLE);
        Instruction inst = instructions.get(instructionId);
        Entity it = findEntity(inst.getTargetIT());
        if (checkWait(it, Location.parse(targetPosKey))) scheduleJointExecution(currentTime, yc, it, inst);
        else yc.setStatus(EntityStatus.WAITING);
    }

    public void handleITExecutionComplete(long currentTime, String entityId, String instructionId) {
        IT it = (IT) entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        Location origin = gridMap.getNodeLocation(inst.getOrigin());

        if (isAtLocation(it, origin)) {
            it.setCurrentLoadWeight(inst.getContainerWeight());
            moveToTarget(currentTime, it, gridMap.getNodeLocation(inst.getDestination()), inst);
        } else {
            it.clearLoad();
            inst.markCompleted();
            taskAllocator.onTaskCompleted(instructionId);

            Instruction next = taskAllocator.assignTask(it);
            if (next != null) {
                instructions.put(next.getInstructionId(), next);
                it.setCurrentInstructionId(next.getInstructionId());
                evaluateStateAndAddEvents(currentTime, it, next);
            } else {
                it.setStatus(EntityStatus.IDLE);
            }
        }
    }

    public void handleITArrival(long currentTime, String entityId, String instructionId, String targetPosKey) {
        Entity it = entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        Location loc = Location.parse(targetPosKey);

        if (Objects.equals(loc, gridMap.getNodeLocation(inst.getDestination()))) {
            Entity qc = findEntity(inst.getTargetQC());
            if (checkWait(qc, loc)) scheduleJointExecution(currentTime, qc, it, inst);
            else it.setStatus(EntityStatus.WAITING);
        } else if (Objects.equals(loc, gridMap.getNodeLocation(inst.getOrigin()))) {
            Entity yc = findEntity(inst.getTargetYC());
            if (checkWait(yc, loc)) scheduleJointExecution(currentTime, yc, it, inst);
            else it.setStatus(EntityStatus.WAITING);
        }
    }

    // --- 辅助 ---

    private boolean checkWait(Entity target, Location loc) {
        return target != null && isAtLocation(target, loc) && target.getStatus() == EntityStatus.WAITING;
    }

    private void handleArrivalLogic(long currentTime, Entity entity, Instruction inst) {
        if (entity.getType() == EntityType.QC) handleQCArrival(currentTime, entity.getId(), inst.getInstructionId(), entity.getCurrentPosition());
        else if (entity.getType() == EntityType.YC) handleYCArrival(currentTime, entity.getId(), inst.getInstructionId(), entity.getCurrentPosition());
        else if (entity.getType() == EntityType.IT) handleITArrival(currentTime, entity.getId(), inst.getInstructionId(), entity.getCurrentPosition());
    }

    private void moveToTarget(long currentTime, Entity entity, Location target, Instruction inst) {
        entity.setStatus(EntityStatus.MOVING);
        List<Location> path = routePlanner.searchRoute(entity.getCurrentLocation(), target);

        // 【核心修改点 1】检查路径是否为空，防止越界异常
        if (path == null || path.isEmpty()) {
            System.err.println(">>> [警告] 寻路失败或路径为空! Entity: " + entity.getId()
                    + " From: " + entity.getCurrentLocation() + " To: " + target);
            // 无法移动，保持 IDLE 状态，避免程序崩溃
            entity.setStatus(EntityStatus.IDLE);
            return;
        }

        entity.setRemainingPath(path);
        processNextStep(currentTime, entity);
    }

    private void scheduleJointExecution(long currentTime, Entity crane, Entity it, Instruction inst) {
        long duration = timeModule.estimateOperationTime(crane, inst); // 使用新接口

        crane.setStatus(EntityStatus.EXECUTING);
        it.setStatus(EntityStatus.EXECUTING);
        long finishTime = currentTime + duration;

        EventType craneEvent = (crane.getType() == EntityType.QC) ? EventType.QC_EXECUTION_COMPLETE : EventType.YC_EXECUTION_COMPLETE;
        pendingEvents.add(new SimEvent(finishTime, craneEvent, crane.getId(), inst.getInstructionId()));
        pendingEvents.add(new SimEvent(finishTime, EventType.IT_EXECUTION_COMPLETE, it.getId(), inst.getInstructionId()));
    }

    public void handleStepArrival(long currentTime, String entityId, String instructionId, String targetPosKey) {
        Entity entity = entities.get(entityId);
        if (entity == null) return;

        Location targetLoc = targetPosKey != null ? Location.parse(targetPosKey) : entity.getCurrentLocation();
        Location oldLoc = entity.getCurrentLocation();
        if (oldLoc != null && !oldLoc.equals(targetLoc)) physicsEngine.unlockSingleResource(entityId, oldLoc);
        entity.setCurrentLocation(targetLoc);
        entity.popNextStep();

        if (!entity.hasRemainingPath()) {
            EventType type = switch (entity.getType()) {
                case QC -> EventType.QC_ARRIVAL;
                case YC -> EventType.YC_ARRIVAL;
                case IT -> EventType.IT_ARRIVAL;
            };
            pendingEvents.add(new SimEvent(currentTime, type, entityId, instructionId, targetLoc.toKey()));
        } else {
            processNextStep(currentTime, entity);
        }
    }

    private void processNextStep(long currentTime, Entity entity) {
        Instruction interrupt = trafficController.checkInterruption(entity);
        if (interrupt != null) return;

        // 【核心修改点 2】双重保险：在获取下一步之前检查路径是否为空
        if (!entity.hasRemainingPath()) {
            return;
        }

        Location nextLoc = entity.getRemainingPath().get(0);
        if (physicsEngine.detectCollision(nextLoc, entity.getId())) {
            Instruction recovery = trafficController.resolveCollision(entity, physicsEngine.getOccupier(nextLoc));
            if (recovery != null) return;
        }

        physicsEngine.lockResources(entity.getId(), Collections.singletonList(nextLoc));
        long duration = timeModule.estimateMovementTime(entity, Collections.singletonList(nextLoc));

        pendingEvents.add(new SimEvent(
                currentTime + duration, EventType.MOVE_STEP,
                entity.getId(), entity.getCurrentInstructionId(), nextLoc.toKey()
        ));
    }

    public void registerEntity(Entity entity) { entities.put(entity.getId(), entity); }
    public void addInstruction(Instruction task) { instructions.put(task.getInstructionId(), task); taskAllocator.onNewTaskSubmitted(task); }
    public SimEvent getNextEvent() { return pendingEvents.poll(); }
    public boolean hasPendingEvents() { return !pendingEvents.isEmpty(); }
    public void handleTaskGeneration(long now) {
        Instruction task = taskGenerator.generate(now);
        if(task != null) addInstruction(task);
        pendingEvents.add(new SimEvent(now + 5000, EventType.TASK_GENERATION, "SYSTEM"));
    }
    private boolean isAtLocation(Entity e, Location loc) { return e.getCurrentLocation() != null && e.getCurrentLocation().equals(loc); }
    private Entity findEntity(String id) { return id == null ? null : entities.get(id); }
}