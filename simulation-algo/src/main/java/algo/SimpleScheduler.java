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
    private final Queue<SimEvent> pendingEvents = new PriorityQueue<>(); // 使用 PriorityQueue 保证时间顺序

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
        // 1. 初始化位置
        for (Entity entity : entities.values()) {
            Location startLoc = gridMap.getNodeLocation(entity.getInitialNodeId());
            if (startLoc == null) startLoc = new Location(0, 0);
            entity.setCurrentLocation(startLoc);
            entity.setStatus(EntityStatus.IDLE);
            physicsEngine.lockResources(entity.getId(), Collections.singletonList(startLoc));
        }
        // 2. 初始分配
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
        // 优先检查当前持有的任务
        if (entity.getCurrentInstructionId() != null) {
            inst = instructions.get(entity.getCurrentInstructionId());
            if (inst == null || "COMPLETED".equals(inst.getStatus())) {
                entity.setCurrentInstructionId(null);
                inst = null;
            }
        }

        // 如果没有任务，向 Allocator 申请
        if (inst == null) {
            inst = taskAllocator.assignTask(entity);
        }

        if (inst == null) {
            entity.setStatus(EntityStatus.IDLE);
            return;
        }

        bindInstruction(entity, inst);
        Location target = getTargetLocation(entity, inst);

        if (target == null) return;

        if (Objects.equals(entity.getCurrentLocation(), target)) {
            handleArrivalLogic(now, entity, inst);
        } else {
            startMove(now, entity, target, inst);
        }
    }

    public void handleCraneExecutionComplete(long now, String entityId, String instructionId) {
        Entity crane = entities.get(entityId);
        Instruction inst = instructions.get(instructionId);

        // 只有 QC 在装船作业完成时，才算整个任务完结
        if (crane.getType() == EntityType.QC && inst.getType() == InstructionType.LOAD_TO_SHIP) {
            finishTask(now, instructionId);
        }

        crane.setStatus(EntityStatus.IDLE);
        tryAssignment(now, crane);
    }

    public void handleITExecutionComplete(long now, String entityId, String instructionId) {
        IT it = (IT) entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        String locType = gridMap.getLocationType(it.getCurrentLocation());

        // 关键逻辑：集卡是否完成了所有阶段？
        // 如果是 LOAD_TO_SHIP，集卡在 BAY 装完货后，不应该结束任务，而是应该去 QUAY
        if ("BAY".equals(locType) && inst.getType() == InstructionType.LOAD_TO_SHIP) {
            it.setCurrentLoadWeight(inst.getContainerWeight()); // 装货
            // 不要 finishTask，继续持有任务
        } else if ("QUAY".equals(locType) && inst.getType() == InstructionType.LOAD_TO_SHIP) {
            it.clearLoad(); // 卸货
            // 此时集卡部分结束，可以释放
        }

        it.setStatus(EntityStatus.IDLE);
        tryAssignment(now, it); // 再次调用时，getTargetLocation 会根据 isLoaded() 返回新目标
    }

    public void handleCraneArrival(long now, String entityId, String instructionId) {
        Entity crane = entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        Entity it = entities.get(inst.getTargetIT());
        // 协同检查
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

        Entity targetCrane = null;
        if ("QUAY".equals(locType)) targetCrane = entities.get(inst.getTargetQC());
        else if ("BAY".equals(locType)) targetCrane = entities.get(inst.getTargetYC());

        if (isEntityAtAndWaiting(targetCrane, loc)) {
            scheduleJointExecution(now, targetCrane, it, inst);
        } else {
            it.setStatus(EntityStatus.WAITING);
        }
    }

    private void startMove(long now, Entity entity, Location target, Instruction inst) {
        List<Location> path = routePlanner.searchRoute(entity.getCurrentLocation(), target);
        if (path == null || path.isEmpty()) {
            // 原地处理
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
            pendingEvents.add(new SimEvent(now, type, entity.getId(), entity.getCurrentInstructionId()));
            return;
        }

        Location next = entity.getRemainingPath().getFirst();
        if (physicsEngine.detectCollision(next, entity.getId())) {
            Instruction wait = trafficController.resolveCollision(entity, physicsEngine.getOccupier(next));
            long waitTime = wait != null ? wait.getExpectedDuration() : 1000;
            pendingEvents.add(new SimEvent(now + waitTime, EventType.MOVE_STEP, entity.getId(), entity.getCurrentInstructionId(), entity.getCurrentLocation().toKey()));
            return;
        }

        Location stepTarget = entity.popNextStep();
        physicsEngine.lockResources(entity.getId(), Collections.singletonList(stepTarget));
        long stepTime = timeModule.estimateMovementTime(entity, Collections.singletonList(stepTarget));

        pendingEvents.add(new SimEvent(now + stepTime, EventType.MOVE_STEP, entity.getId(), entity.getCurrentInstructionId(), stepTarget.toKey()));
    }

    private void scheduleJointExecution(long now, Entity crane, Entity it, Instruction inst) {
        inst.markInProgress(now);
        long duration = timeModule.estimateOperationTime(crane, inst);
        crane.setStatus(EntityStatus.EXECUTING);
        it.setStatus(EntityStatus.EXECUTING);

        // 注意：载重更新移到了 handleITExecutionComplete 以确保逻辑清晰

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
        processNextMoveStep(now, entity);
    }

    public void handleTaskGeneration(long now) {
        Instruction task = taskGenerator.generate(now);
        if (task != null) {
            addInstruction(task);
            System.out.println(">>> [" + now + "] 生成任务: " + task.getInstructionId());
            // 唤醒所有空闲设备
            for (Entity e : entities.values()) {
                if (e.getStatus() == EntityStatus.IDLE) tryAssignment(now, e);
            }
        }
        pendingEvents.add(new SimEvent(now + 15000, EventType.TASK_GENERATION, "SYSTEM"));
    }

    private Location getTargetLocation(Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) return gridMap.getNodeLocation(i.getDestination());
        if (e.getType() == EntityType.YC) return gridMap.getNodeLocation(i.getOrigin());
        if (e.getType() == EntityType.IT) {
            IT it = (IT) e;
            // LOAD_TO_SHIP: 没货去 Origin (装), 有货去 Destination (卸)
            return it.isLoaded() ? gridMap.getNodeLocation(i.getDestination()) : gridMap.getNodeLocation(i.getOrigin());
        }
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
            System.out.println(">>> [" + now + "] 任务完成: " + iid);
        }
    }

    private boolean isEntityAtAndWaiting(Entity e, Location loc) {
        return e != null && e.getCurrentLocation() != null && e.getCurrentLocation().equals(loc)
                && (e.getStatus() == EntityStatus.WAITING || e.getStatus() == EntityStatus.IDLE);
    }

    private void handleArrivalLogic(long now, Entity e, Instruction i) {
        if (e.getType() == EntityType.QC || e.getType() == EntityType.YC) handleCraneArrival(now, e.getId(), i.getInstructionId());
        else if (e.getType() == EntityType.IT) handleITArrival(now, e.getId(), i.getInstructionId());
    }
}