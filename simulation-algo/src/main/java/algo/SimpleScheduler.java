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

/**
 * 核心调度器 - 优化版
 * [修复] 移除所有IDE警告，逻辑去重，代码优雅
 */
public class SimpleScheduler {
    private final TaskAllocator taskAllocator;
    private final TrafficController trafficController;
    private final RoutePlanner routePlanner;
    private final TimeEstimationModule timeModule;
    private final PhysicsEngine physicsEngine;
    private final GridMap gridMap;
    private final TaskGenerator taskGenerator;

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

    public void registerEntity(Entity entity) { entities.put(entity.getId(), entity); }
    public void addInstruction(Instruction task) {
        instructions.put(task.getInstructionId(), task);
        taskAllocator.onNewTaskSubmitted(task);
    }
    public SimEvent getNextEvent() { return pendingEvents.poll(); }
    public boolean hasPendingEvents() { return !pendingEvents.isEmpty(); }

    public void init() {
        for (Entity entity : entities.values()) {
            Instruction inst = taskAllocator.assignTask(entity);
            if (inst == null) {
                entity.setStatus(EntityStatus.IDLE);
                continue;
            }
            bindInstruction(entity, inst);

            Location target = getTargetLocation(entity, inst);
            if (Objects.equals(entity.getCurrentLocation(), target)) {
                handleArrivalLogic(0, entity, inst);
            } else {
                startMove(0, entity, target, inst);
            }
        }
        if (taskGenerator != null) {
            pendingEvents.add(new SimEvent(0, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    // --- 业务逻辑 ---

    public void handleCraneExecutionComplete(long now, String entityId, String instructionId) {
        Entity crane = entities.get(entityId);
        finishTask(instructionId);

        Instruction nextInst = taskAllocator.assignTask(crane);
        if (nextInst == null) {
            crane.setStatus(EntityStatus.IDLE);
            return;
        }
        bindInstruction(crane, nextInst);

        Location currentLoc = crane.getCurrentLocation();
        Location targetLoc = getTargetLocation(crane, nextInst);

        if (Objects.equals(currentLoc, targetLoc)) {
            Entity it = entities.get(nextInst.getTargetIT());
            if (isEntityAtAndWaiting(it, currentLoc)) {
                scheduleJointExecution(now, crane, it, nextInst);
            } else {
                crane.setStatus(EntityStatus.WAITING);
            }
        } else {
            startMove(now, crane, targetLoc, nextInst);
        }
    }

    public void handleITExecutionComplete(long now, String entityId, String instructionId) {
        IT it = (IT) entities.get(entityId);
        Instruction currentInst = instructions.get(instructionId);
        boolean isTaskFullyDone = false;

        // [优化] 移除空 else 分支，逻辑更紧凑
        if (currentInst.getType() == InstructionType.LOAD_TO_SHIP) {
            if (it.isLoaded() && isAtLocation(it, gridMap.getNodeLocation(currentInst.getDestination()))) {
                it.clearLoad();
                isTaskFullyDone = true;
            }
        } else {
            isTaskFullyDone = true;
        }

        if (isTaskFullyDone) {
            finishTask(instructionId);
            currentInst = taskAllocator.assignTask(it);
        }

        if (currentInst == null) {
            it.setStatus(EntityStatus.IDLE);
            return;
        }
        bindInstruction(it, currentInst);

        Location target = getTargetLocation(it, currentInst);
        startMove(now, it, target, currentInst);
    }

    public void handleCraneArrival(long now, String entityId, String instructionId) {
        Entity crane = entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        Entity it = entities.get(inst.getTargetIT());

        if (isEntityAtAndWaiting(it, crane.getCurrentLocation())) {
            scheduleJointExecution(now, crane, it, inst);
        } else {
            crane.setStatus(EntityStatus.WAITING);
        }
    }

    public void handleITArrival(long now, String entityId, String instructionId) {
        IT it = (IT) entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        Location loc = it.getCurrentLocation();
        String locType = gridMap.getLocationType(loc);

        if ("QUAY".equals(locType)) {
            Entity qc = entities.get(inst.getTargetQC());
            if (isEntityAtAndWaiting(qc, loc)) scheduleJointExecution(now, qc, it, inst);
            else it.setStatus(EntityStatus.WAITING);
        } else if ("BAY".equals(locType)) {
            Entity yc = entities.get(inst.getTargetYC());
            if (isEntityAtAndWaiting(yc, loc)) scheduleJointExecution(now, yc, it, inst);
            else it.setStatus(EntityStatus.WAITING);
        } else {
            it.setStatus(EntityStatus.WAITING);
        }
    }

    // --- 核心方法 ---

    private void startMove(long now, Entity entity, Location target, Instruction inst) {
        List<Location> path = routePlanner.searchRoute(entity.getCurrentLocation(), target);
        if (path == null || path.isEmpty()) {
            handleArrivalLogic(now, entity, inst);
            return;
        }
        entity.setStatus(EntityStatus.MOVING);
        entity.setRemainingPath(path);
        processNextMoveStep(now, entity);
    }

    private void processNextMoveStep(long now, Entity entity) {
        if (trafficController.checkInterruption(entity) != null) return;

        if (!entity.hasRemainingPath()) {
            Location curr = entity.getCurrentLocation();
            EventType type = switch (entity.getType()) {
                case QC -> EventType.QC_ARRIVAL;
                case YC -> EventType.YC_ARRIVAL;
                case IT -> EventType.IT_ARRIVAL;
            };
            pendingEvents.add(new SimEvent(now, type, entity.getId(), entity.getCurrentInstructionId(), curr.toKey()));
            return;
        }

        // [修复] 使用 getFirst() 替代 get(0)
        Location next = entity.getRemainingPath().getFirst();

        if (physicsEngine.detectCollision(next, entity.getId())) {
            trafficController.resolveCollision(entity, physicsEngine.getOccupier(next));
            return;
        }

        physicsEngine.lockResources(entity.getId(), Collections.singletonList(next));
        long stepTime = timeModule.estimateMovementTime(entity, Collections.singletonList(next));

        pendingEvents.add(new SimEvent(now + stepTime, EventType.MOVE_STEP,
                entity.getId(), entity.getCurrentInstructionId(), next.toKey()));
    }

    private void scheduleJointExecution(long now, Entity crane, Entity it, Instruction inst) {
        long duration = timeModule.estimateOperationTime(crane, inst);
        crane.setStatus(EntityStatus.EXECUTING);
        it.setStatus(EntityStatus.EXECUTING);

        if (crane.getType() == EntityType.YC && inst.getType() == InstructionType.LOAD_TO_SHIP) {
            ((IT)it).setCurrentLoadWeight(inst.getContainerWeight());
        }

        long finishTime = now + duration;
        EventType craneEvent = (crane.getType() == EntityType.QC) ?
                EventType.QC_EXECUTION_COMPLETE : EventType.YC_EXECUTION_COMPLETE;

        pendingEvents.add(new SimEvent(finishTime, craneEvent, crane.getId(), inst.getInstructionId()));
        pendingEvents.add(new SimEvent(finishTime, EventType.IT_EXECUTION_COMPLETE, it.getId(), inst.getInstructionId()));
    }

    // [修复] 移除了未使用的 iid 参数
    public void handleStepArrival(long now, String eid, String posKey) {
        Entity entity = entities.get(eid);
        Location target = Location.parse(posKey);
        Location old = entity.getCurrentLocation();

        if (old != null && !old.equals(target)) physicsEngine.unlockSingleResource(eid, old);
        entity.setCurrentLocation(target);
        entity.popNextStep();

        processNextMoveStep(now, entity);
    }

    public void handleTaskGeneration(long now) {
        Instruction task = taskGenerator.generate(now);
        if (task != null) addInstruction(task);
        pendingEvents.add(new SimEvent(now + 5000, EventType.TASK_GENERATION, "SYSTEM"));
    }

    private Location getTargetLocation(Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) return gridMap.getNodeLocation(i.getDestination());
        if (e.getType() == EntityType.YC) return gridMap.getNodeLocation(i.getOrigin());
        if (e.getType() == EntityType.IT) {
            IT it = (IT)e;
            return it.isLoaded() ? gridMap.getNodeLocation(i.getDestination())
                    : gridMap.getNodeLocation(i.getOrigin());
        }
        return e.getCurrentLocation();
    }

    private void bindInstruction(Entity e, Instruction i) {
        instructions.put(i.getInstructionId(), i);
        e.setCurrentInstructionId(i.getInstructionId());
    }

    // [修复] 填充了方法体，不再是空代码块
    private void finishTask(String iid) {
        if (iid != null && instructions.containsKey(iid)) {
            instructions.get(iid).markCompleted();
            taskAllocator.onTaskCompleted(iid);
        }
    }

    private boolean isEntityAtAndWaiting(Entity e, Location loc) {
        return e != null && isAtLocation(e, loc) && e.getStatus() == EntityStatus.WAITING;
    }

    private boolean isAtLocation(Entity e, Location loc) {
        return e.getCurrentLocation() != null && e.getCurrentLocation().equals(loc);
    }

    private void handleArrivalLogic(long now, Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) handleCraneArrival(now, e.getId(), i.getInstructionId());
        else if (e.getType() == EntityType.YC) handleCraneArrival(now, e.getId(), i.getInstructionId());
        else if (e.getType() == EntityType.IT) handleITArrival(now, e.getId(), i.getInstructionId());
    }
}