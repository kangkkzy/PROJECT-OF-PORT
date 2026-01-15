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

    public void init() {
        for (Entity entity : entities.values()) {
            // 修复：初始化实体的 Location
            Location startLoc = gridMap.getNodeLocation(entity.getInitialNodeId());
            entity.setCurrentLocation(startLoc);
            tryAssignment(0, entity);
        }
        if (taskGenerator != null) {
            pendingEvents.add(new SimEvent(0, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    private void tryAssignment(long now, Entity entity) {
        Instruction inst = taskAllocator.assignTask(entity);
        if (inst == null) {
            entity.setStatus(EntityStatus.IDLE);
            return;
        }
        bindInstruction(entity, inst);
        Location target = getTargetLocation(entity, inst);

        if (Objects.equals(entity.getCurrentLocation(), target)) {
            handleArrivalLogic(now, entity, inst);
        } else {
            startMove(now, entity, target, inst);
        }
    }

    public void handleCraneExecutionComplete(long now, String entityId, String instructionId) {
        Entity crane = entities.get(entityId);
        finishTask(now, instructionId);
        tryAssignment(now, crane);
    }

    public void handleITExecutionComplete(long now, String entityId, String instructionId) {
        IT it = (IT) entities.get(entityId);
        finishTask(now, instructionId);
        tryAssignment(now, it);
    }

    public void handleCraneArrival(long now, String entityId, String instructionId) {
        Entity crane = entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        Entity it = entities.get(inst.getTargetIT());
        if (isEntityAtAndWaiting(it, crane.getCurrentLocation())) scheduleJointExecution(now, crane, it, inst);
        else crane.setStatus(EntityStatus.WAITING);
    }

    public void handleITArrival(long now, String entityId, String instructionId) {
        IT it = (IT) entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        Location loc = it.getCurrentLocation();
        String locType = gridMap.getLocationType(loc);
        Entity crane = ("QUAY".equals(locType)) ? entities.get(inst.getTargetQC()) : entities.get(inst.getTargetYC());
        if (isEntityAtAndWaiting(crane, loc)) scheduleJointExecution(now, crane, it, inst);
        else it.setStatus(EntityStatus.WAITING);
    }

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
            EventType type = switch (entity.getType()) {
                case QC -> EventType.QC_ARRIVAL;
                case YC -> EventType.YC_ARRIVAL;
                case IT -> EventType.IT_ARRIVAL;
            };
            pendingEvents.add(new SimEvent(now, type, entity.getId(), entity.getCurrentInstructionId(), entity.getCurrentLocation().toKey()));
            return;
        }
        Location next = entity.getRemainingPath().getFirst();
        if (physicsEngine.detectCollision(next, entity.getId())) {
            trafficController.resolveCollision(entity, physicsEngine.getOccupier(next));
            return;
        }
        physicsEngine.lockResources(entity.getId(), Collections.singletonList(next));
        long stepTime = timeModule.estimateMovementTime(entity, Collections.singletonList(next));
        pendingEvents.add(new SimEvent(now + stepTime, EventType.MOVE_STEP, entity.getId(), entity.getCurrentInstructionId(), next.toKey()));
    }

    private void scheduleJointExecution(long now, Entity crane, Entity it, Instruction inst) {
        inst.markInProgress(now);
        long duration = timeModule.estimateOperationTime(crane, inst);
        crane.setStatus(EntityStatus.EXECUTING);
        it.setStatus(EntityStatus.EXECUTING);
        if (crane.getType() == EntityType.YC && inst.getType() == InstructionType.LOAD_TO_SHIP) {
            ((IT)it).setCurrentLoadWeight(inst.getContainerWeight());
        }
        long finishTime = now + duration;
        EventType craneEvent = (crane.getType() == EntityType.QC) ? EventType.QC_EXECUTION_COMPLETE : EventType.YC_EXECUTION_COMPLETE;
        pendingEvents.add(new SimEvent(finishTime, craneEvent, crane.getId(), inst.getInstructionId()));
        pendingEvents.add(new SimEvent(finishTime, EventType.IT_EXECUTION_COMPLETE, it.getId(), inst.getInstructionId()));
    }

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
        if (task != null) {
            addInstruction(task);
            // 修复：生成任务后立即唤醒 IDLE 设备
            for (Entity e : entities.values()) {
                if (e.getStatus() == EntityStatus.IDLE) tryAssignment(now, e);
            }
        }
        pendingEvents.add(new SimEvent(now + 5000, EventType.TASK_GENERATION, "SYSTEM"));
    }

    private Location getTargetLocation(Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) return gridMap.getNodeLocation(i.getDestination());
        if (e.getType() == EntityType.YC) return gridMap.getNodeLocation(i.getOrigin());
        if (e.getType() == EntityType.IT) return ((IT)e).isLoaded() ? gridMap.getNodeLocation(i.getDestination()) : gridMap.getNodeLocation(i.getOrigin());
        return e.getCurrentLocation();
    }

    private void bindInstruction(Entity e, Instruction i) {
        instructions.put(i.getInstructionId(), i);
        e.setCurrentInstructionId(i.getInstructionId());
    }

    private void finishTask(long now, String iid) {
        if (iid != null && instructions.containsKey(iid)) {
            instructions.get(iid).markCompleted(now);
            taskAllocator.onTaskCompleted(iid);
        }
    }

    private boolean isEntityAtAndWaiting(Entity e, Location loc) {
        return e != null && isAtLocation(e, loc) && (e.getStatus() == EntityStatus.WAITING || e.getStatus() == EntityStatus.IDLE);
    }

    private boolean isAtLocation(Entity e, Location loc) {
        return e.getCurrentLocation() != null && e.getCurrentLocation().equals(loc);
    }

    private void handleArrivalLogic(long now, Entity e, Instruction i) {
        if (e.getType() == EntityType.QC || e.getType() == EntityType.YC) handleCraneArrival(now, e.getId(), i.getInstructionId());
        else if (e.getType() == EntityType.IT) handleITArrival(now, e.getId(), i.getInstructionId());
    }
}