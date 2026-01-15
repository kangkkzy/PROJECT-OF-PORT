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
    private final Queue<SimEvent> pendingEvents = new PriorityQueue<>();

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

    public void registerEntity(Entity entity) {
        if (entity != null && entity.getId() != null) entities.put(entity.getId(), entity);
    }

    public void addInstruction(Instruction task) {
        if (task != null && task.getInstructionId() != null) {
            instructions.put(task.getInstructionId(), task);
            taskAllocator.onNewTaskSubmitted(task);
        }
    }

    public SimEvent getNextEvent() { return pendingEvents.poll(); }

    public void init() {
        for (Entity entity : entities.values()) {
            Location startLoc = gridMap.getNodeLocation(entity.getInitialNodeId());
            if (startLoc == null) startLoc = new Location(0, 0);
            entity.setCurrentLocation(startLoc);
            entity.setStatus(EntityStatus.IDLE);
            physicsEngine.lockResources(entity.getId(), Collections.singletonList(startLoc));
        }
        for (Entity entity : entities.values()) {
            tryAssignment(0, entity);
        }
        if (taskGenerator != null) {
            pendingEvents.add(new SimEvent(0, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    private void tryAssignment(long now, Entity entity) {
        if (entity.getStatus() == EntityStatus.MOVING || entity.getStatus() == EntityStatus.EXECUTING) return;

        Instruction inst = null;
        if (entity.getCurrentInstructionId() != null) {
            inst = instructions.get(entity.getCurrentInstructionId());
            if (inst == null || "COMPLETED".equals(inst.getStatus())) {
                entity.setCurrentInstructionId(null);
                inst = null;
            }
        }

        if (inst == null) inst = taskAllocator.assignTask(entity);

        if (inst == null) {
            entity.setStatus(EntityStatus.IDLE);
            // 关键：没任务时，看看脚下有没有人在等我
            checkAndWakeUpPartners(now, entity);
            return;
        }

        bindInstruction(entity, inst);
        Location target = getTargetLocation(entity, inst);

        if (target == null) return;

        if (target.equals(entity.getCurrentLocation())) {
            handleArrivalLogic(now, entity, inst);
        } else {
            startMove(now, entity, target, inst);
        }
    }

    private void startMove(long now, Entity entity, Location target, Instruction inst) {
        List<Location> path = routePlanner.searchRoute(entity.getCurrentLocation(), target);
        if (path == null || path.isEmpty()) {
            if (entity.getCurrentLocation().equals(target)) {
                handleArrivalLogic(now, entity, inst);
            } else {
                pendingEvents.add(new SimEvent(now + 2000, EventType.MOVE_STEP,
                        entity.getId(), entity.getCurrentInstructionId(), entity.getCurrentLocation().toKey()));
            }
            return;
        }
        entity.setStatus(EntityStatus.MOVING);
        entity.setRemainingPath(path);
        processNextMoveStep(now, entity);
    }

    private void processNextMoveStep(long now, Entity entity) {
        if (trafficController.checkInterruption(entity) != null) return;

        if (!entity.hasRemainingPath()) {
            triggerArrivalEvent(now, entity);
            return;
        }

        Location next = entity.getRemainingPath().get(0);
        Instruction inst = instructions.get(entity.getCurrentInstructionId());
        Location goal = entity.getRemainingPath().get(entity.getRemainingPath().size() - 1);

        // 协同豁免逻辑
        if (physicsEngine.detectCollision(next, entity.getId(), inst, goal)) {
            String occupier = physicsEngine.getOccupier(next);
            if (!isCooperativeMove(entity, occupier)) {
                Instruction resolution = trafficController.resolveCollision(entity, occupier);
                long waitTime = (resolution != null) ? resolution.getExpectedDuration() : 1000;
                pendingEvents.add(new SimEvent(now + waitTime, EventType.MOVE_STEP,
                        entity.getId(), entity.getCurrentInstructionId(), entity.getCurrentLocation().toKey()));
                return;
            }
        }

        Location stepTarget = entity.popNextStep();
        physicsEngine.lockResources(entity.getId(), Collections.singletonList(stepTarget));
        long stepTime = timeModule.estimateMovementTime(entity, Collections.singletonList(stepTarget));

        pendingEvents.add(new SimEvent(now + stepTime, EventType.MOVE_STEP,
                entity.getId(), entity.getCurrentInstructionId(), stepTarget.toKey()));
    }

    public void handleStepArrival(long now, String eid, String posKey) {
        Entity entity = entities.get(eid);
        Location target = Location.parse(posKey);
        Location old = entity.getCurrentLocation();
        if (old != null && !old.equals(target)) {
            physicsEngine.unlockSingleResource(eid, old);
        }
        entity.setCurrentLocation(target);
        processNextMoveStep(now, entity);
    }

    // --- 核心修复：更宽容的唤醒机制 ---
    private void checkAndWakeUpPartners(long now, Entity me) {
        for (Entity other : entities.values()) {
            if (other.getId().equals(me.getId())) continue;

            // 只要位置重合，且对方是空闲或等待状态，都尝试握手
            // 修复点：移除了 other.getStatus() == WAITING 的严格限制
            if (other.getCurrentLocation().equals(me.getCurrentLocation())) {
                if (other.getStatus() == EntityStatus.WAITING || other.getStatus() == EntityStatus.IDLE) {
                    attemptJointExecution(now, me, other);
                }
            }
        }
    }

    private void attemptJointExecution(long now, Entity e1, Entity e2) {
        Entity crane = null, it = null;
        if (isCrane(e1) && e2.getType() == EntityType.IT) { crane = e1; it = e2; }
        else if (isCrane(e2) && e1.getType() == EntityType.IT) { crane = e2; it = e1; }

        if (crane != null && it != null) {
            Instruction iCrane = instructions.get(crane.getCurrentInstructionId());
            Instruction iIT = instructions.get(it.getCurrentInstructionId());

            // 情况1：双方持有同一任务
            if (iCrane != null && iIT != null && iCrane.getInstructionId().equals(iIT.getInstructionId())) {
                if (canExecute(crane) && canExecute(it)) {
                    scheduleJointExecution(now, crane, it, iCrane);
                }
            }
            // 情况2：IT有任务，Crane空闲（被动激活）
            else if (iIT != null && iCrane == null && canExecute(crane) && canExecute(it)) {
                String targetCraneId = (crane.getType() == EntityType.QC) ? iIT.getTargetQC() : iIT.getTargetYC();
                if (crane.getId().equals(targetCraneId)) {
                    bindInstruction(crane, iIT);
                    scheduleJointExecution(now, crane, it, iIT);
                }
            }
        }
    }

    private boolean isCrane(Entity e) { return e.getType() == EntityType.QC || e.getType() == EntityType.YC; }
    private boolean canExecute(Entity e) { return e.getStatus() == EntityStatus.IDLE || e.getStatus() == EntityStatus.WAITING; }

    public void handleCraneExecutionComplete(long now, String entityId, String instructionId) {
        Entity crane = entities.get(entityId);
        Instruction inst = instructions.get(instructionId);

        // 标记该部分已完成，防止 YC 重复领取
        markPartComplete(crane, instructionId);

        if (crane.getType() == EntityType.QC && inst.getType() == InstructionType.LOAD_TO_SHIP) {
            completeTask(now, instructionId);
        }

        crane.setStatus(EntityStatus.IDLE);
        // 先解除当前指令绑定，否则 tryAssignment 可能误判
        crane.setCurrentInstructionId(null);

        tryAssignment(now, crane);
        checkAndWakeUpPartners(now, crane);
    }

    public void handleITExecutionComplete(long now, String entityId, String instructionId) {
        IT it = (IT) entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        String locType = gridMap.getLocationType(it.getCurrentLocation());

        if ("BAY".equals(locType)) {
            it.setCurrentLoadWeight(inst.getContainerWeight());
        } else if ("QUAY".equals(locType)) {
            it.clearLoad();
            it.setCurrentInstructionId(null);
            // 只有在 QUAY 卸货后，集卡才算真正脱离任务
            markPartComplete(it, instructionId);
        }

        it.setStatus(EntityStatus.IDLE);
        tryAssignment(now, it);
    }

    // 调用 Dispatcher 的方法记录完成情况
    private void markPartComplete(Entity e, String iid) {
        if (taskAllocator instanceof plugins.FifoTaskDispatcher d) {
            d.markPartCompleted(iid, e.getId());
        }
    }

    public void handleArrivalLogic(long now, Entity e, Instruction i) {
        e.setStatus(EntityStatus.WAITING);
        checkAndWakeUpPartners(now, e);
    }
    public void handleCraneArrival(long now, String entityId, String instructionId) {
        handleArrivalLogic(now, entities.get(entityId), instructions.get(instructionId));
    }
    public void handleITArrival(long now, String entityId, String instructionId) {
        handleArrivalLogic(now, entities.get(entityId), instructions.get(instructionId));
    }

    private void scheduleJointExecution(long now, Entity crane, Entity it, Instruction inst) {
        inst.markInProgress(now);
        long duration = timeModule.estimateOperationTime(crane, inst);
        crane.setStatus(EntityStatus.EXECUTING);
        it.setStatus(EntityStatus.EXECUTING);

        long finishTime = now + duration;
        EventType craneEvent = (crane.getType() == EntityType.QC) ? EventType.QC_EXECUTION_COMPLETE : EventType.YC_EXECUTION_COMPLETE;
        pendingEvents.add(new SimEvent(finishTime, craneEvent, crane.getId(), inst.getInstructionId()));
        pendingEvents.add(new SimEvent(finishTime, EventType.IT_EXECUTION_COMPLETE, it.getId(), inst.getInstructionId()));
    }

    private void triggerArrivalEvent(long now, Entity entity) {
        EventType type = switch (entity.getType()) {
            case QC -> EventType.QC_ARRIVAL;
            case YC -> EventType.YC_ARRIVAL;
            case IT -> EventType.IT_ARRIVAL;
        };
        pendingEvents.add(new SimEvent(now, type, entity.getId(), entity.getCurrentInstructionId()));
    }

    private Location getTargetLocation(Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) return gridMap.getNodeLocation(i.getDestination());
        if (e.getType() == EntityType.YC) return gridMap.getNodeLocation(i.getOrigin());
        if (e.getType() == EntityType.IT) {
            IT it = (IT) e;
            return it.isLoaded() ? gridMap.getNodeLocation(i.getDestination()) : gridMap.getNodeLocation(i.getOrigin());
        }
        return e.getCurrentLocation();
    }

    private void bindInstruction(Entity e, Instruction i) {
        instructions.put(i.getInstructionId(), i);
        e.setCurrentInstructionId(i.getInstructionId());
        if ("PENDING".equals(i.getStatus())) i.setStatus("ASSIGNED");
    }

    private void completeTask(long now, String iid) {
        Instruction inst = instructions.get(iid);
        if (inst != null) {
            inst.markCompleted(now);
            taskAllocator.onTaskCompleted(iid);
            System.out.println(">>> [" + now + "] 任务完成! Total_Throughput +1. ID: " + iid);
        }
    }

    public void handleTaskGeneration(long now) {
        Instruction task = taskGenerator.generate(now);
        if (task != null) {
            addInstruction(task);
            System.out.println(">>> [" + now + "] 新任务: " + task.getInstructionId());
            for (Entity e : entities.values()) {
                if (e.getStatus() == EntityStatus.IDLE) tryAssignment(now, e);
            }
        }
        pendingEvents.add(new SimEvent(now + 15000, EventType.TASK_GENERATION, "SYSTEM"));
    }

    private boolean isCooperativeMove(Entity mover, String occupierId) {
        if (occupierId == null) return false;
        Instruction inst = instructions.get(mover.getCurrentInstructionId());
        if (inst == null) return false;
        if (mover.getType() == EntityType.IT) return occupierId.equals(inst.getTargetQC()) || occupierId.equals(inst.getTargetYC());
        if (mover.getType() == EntityType.YC || mover.getType() == EntityType.QC) return occupierId.equals(inst.getTargetIT());
        return false;
    }
}