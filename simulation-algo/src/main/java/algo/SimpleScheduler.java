package algo;

import decision.*;
import entity.*;
import Instruction.*;
import map.GridMap;
import map.Location;
import physics.PhysicsEngine;
import event.EventType;
import event.SimEvent;
import plugins.FifoTaskDispatcher;
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
        if (entity != null && entity.getId() != null) {
            entities.put(entity.getId(), entity);
        }
    }

    public void addInstruction(Instruction task) {
        if (task != null && task.getInstructionId() != null) {
            instructions.put(task.getInstructionId(), task);
            taskAllocator.onNewTaskSubmitted(task);
        }
    }

    public SimEvent getNextEvent() { return pendingEvents.poll(); }

    public void init() {
        // 阶段一：初始化位置
        for (Entity entity : entities.values()) {
            String initNodeId = entity.getInitialNodeId();
            Location startLoc = null;
            if (initNodeId != null) {
                startLoc = gridMap.getNodeLocation(initNodeId);
            }
            if (startLoc == null) {
                System.err.println("错误: 实体 " + entity.getId() + " 初始位置无效: " + initNodeId);
                startLoc = new Location(0, 0);
            }
            entity.setCurrentLocation(startLoc);
            entity.setStatus(EntityStatus.IDLE);
            physicsEngine.lockResources(entity.getId(), Collections.singletonList(startLoc));
        }

        // 阶段二：分配任务
        for (Entity entity : entities.values()) {
            tryAssignment(0, entity);
        }

        if (taskGenerator != null) {
            pendingEvents.add(new SimEvent(0, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    private void tryAssignment(long now, Entity entity) {
        if (entity.getStatus() == EntityStatus.MOVING || entity.getStatus() == EntityStatus.EXECUTING) return;

        Instruction inst = taskAllocator.assignTask(entity);
        if (inst == null) {
            entity.setStatus(EntityStatus.IDLE);
            return;
        }

        bindInstruction(entity, inst);
        Location target = getTargetLocation(entity, inst);

        if (target == null) {
            System.err.println("警告: 无法计算实体 " + entity.getId() + " 的目标位置");
            return;
        }

        if (target.equals(entity.getCurrentLocation())) {
            handleArrivalLogic(now, entity, inst);
        } else {
            startMove(now, entity, target, inst);
        }
    }

    public void handleTaskGeneration(long now) {
        Instruction task = taskGenerator.generate(now);
        if (task != null) {
            addInstruction(task);
            System.out.println(">>> [" + now + "] 生成新任务: " + task.getInstructionId() +
                    " [Origin:" + task.getOrigin() + " -> Dest:" + task.getDestination() + "]");

            // 唤醒所有 IDLE 设备
            for (Entity e : entities.values()) {
                if (e.getStatus() == EntityStatus.IDLE || e.getStatus() == EntityStatus.WAITING) {
                    tryAssignment(now, e);
                }
            }
        }
        pendingEvents.add(new SimEvent(now + 15000, EventType.TASK_GENERATION, "SYSTEM"));
    }

    private void startMove(long now, Entity entity, Location target, Instruction inst) {
        List<Location> path = routePlanner.searchRoute(entity.getCurrentLocation(), target);
        if (path == null || path.isEmpty()) {
            handleArrivalLogic(now, entity, inst); // 就在原地
            return;
        }
        entity.setStatus(EntityStatus.MOVING);
        entity.setRemainingPath(path);
        processNextMoveStep(now, entity);
    }

    private void processNextMoveStep(long now, Entity entity) {
        Instruction interrupt = trafficController.checkInterruption(entity);
        if (interrupt != null && interrupt.getType() == InstructionType.WAIT) {
            pendingEvents.add(new SimEvent(now + interrupt.getExpectedDuration(), EventType.MOVE_STEP,
                    entity.getId(), entity.getCurrentInstructionId(), entity.getCurrentLocation().toKey()));
            return;
        }

        if (!entity.hasRemainingPath()) {
            triggerArrivalEvent(now, entity);
            return;
        }

        Location next = entity.getRemainingPath().get(0);
        if (physicsEngine.detectCollision(next, entity.getId())) {
            Instruction resolution = trafficController.resolveCollision(entity, physicsEngine.getOccupier(next));
            long waitTime = (resolution != null) ? resolution.getExpectedDuration() : 1000;
            pendingEvents.add(new SimEvent(now + waitTime, EventType.MOVE_STEP,
                    entity.getId(), entity.getCurrentInstructionId(), entity.getCurrentLocation().toKey()));
            return;
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

    private void triggerArrivalEvent(long now, Entity entity) {
        EventType type = switch (entity.getType()) {
            case QC -> EventType.QC_ARRIVAL;
            case YC -> EventType.YC_ARRIVAL;
            case IT -> EventType.IT_ARRIVAL;
        };
        pendingEvents.add(new SimEvent(now, type, entity.getId(), entity.getCurrentInstructionId()));
    }

    private void handleArrivalLogic(long now, Entity e, Instruction i) {
        if (e.getType() == EntityType.QC || e.getType() == EntityType.YC) {
            handleCraneArrival(now, e.getId(), i.getInstructionId());
        } else if (e.getType() == EntityType.IT) {
            handleITArrival(now, e.getId(), i.getInstructionId());
        }
    }

    public void handleCraneArrival(long now, String entityId, String instructionId) {
        Entity crane = entities.get(entityId);
        Instruction inst = instructions.get(instructionId);
        Entity it = entities.get(inst.getTargetIT());

        if (isCooperatingEntityReady(it, crane.getCurrentLocation())) {
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

        if (isCooperatingEntityReady(targetCrane, loc)) {
            scheduleJointExecution(now, targetCrane, it, inst);
        } else {
            // 调试信息：为什么没握手成功？
            if (targetCrane != null && !targetCrane.getCurrentLocation().equals(loc)) {
                System.out.println("警告: IT到达 " + loc + " 但目标 " + targetCrane.getId() + " 在 " + targetCrane.getCurrentLocation());
            }
            it.setStatus(EntityStatus.WAITING);
        }
    }

    private boolean isCooperatingEntityReady(Entity partner, Location loc) {
        return partner != null &&
                partner.getCurrentLocation() != null &&
                partner.getCurrentLocation().equals(loc) &&
                (partner.getStatus() == EntityStatus.WAITING || partner.getStatus() == EntityStatus.IDLE);
    }

    private void scheduleJointExecution(long now, Entity crane, Entity it, Instruction inst) {
        inst.markInProgress(now);
        long duration = timeModule.estimateOperationTime(crane, inst);
        crane.setStatus(EntityStatus.EXECUTING);
        it.setStatus(EntityStatus.EXECUTING);

        // 注意：这里不再立即修改 IT 载重，改为在任务完成时修改

        long finishTime = now + duration;
        EventType craneEvent = (crane.getType() == EntityType.QC) ? EventType.QC_EXECUTION_COMPLETE : EventType.YC_EXECUTION_COMPLETE;

        pendingEvents.add(new SimEvent(finishTime, craneEvent, crane.getId(), inst.getInstructionId()));
        pendingEvents.add(new SimEvent(finishTime, EventType.IT_EXECUTION_COMPLETE, it.getId(), inst.getInstructionId()));
    }

    public void handleCraneExecutionComplete(long now, String entityId, String instructionId) {
        Entity crane = entities.get(entityId);
        markPartComplete(crane, instructionId);
        Instruction inst = instructions.get(instructionId);

        if (crane.getType() == EntityType.QC && inst.getType() == InstructionType.LOAD_TO_SHIP) {
            completeInstruction(now, instructionId);
        }

        crane.setStatus(EntityStatus.IDLE);
        tryAssignment(now, crane);
    }

    public void handleITExecutionComplete(long now, String entityId, String instructionId) {
        IT it = (IT) entities.get(entityId);
        Instruction inst = instructions.get(instructionId);

        // 1. 根据当前位置更新载重状态 (更符合物理逻辑)
        String locType = gridMap.getLocationType(it.getCurrentLocation());
        if ("BAY".equals(locType)) {
            it.setCurrentLoadWeight(inst.getContainerWeight()); // 装货完成
        } else if ("QUAY".equals(locType)) {
            it.clearLoad(); // 卸货完成
        }

        // 2. 判断任务流是否终结
        Location destLoc = gridMap.getNodeLocation(inst.getDestination());
        boolean isAtDestination = destLoc != null && destLoc.equals(it.getCurrentLocation());

        if (isAtDestination) {
            markPartComplete(it, instructionId);
            // 只有在终点卸货完成，IT 的任务才算结束
        }

        it.setStatus(EntityStatus.IDLE);
        // 3. 立即尝试申请下一个动作 (如果刚装完货，assignTask 会返回同一个任务，getTargetLocation 会指向终点)
        tryAssignment(now, it);
    }

    private void markPartComplete(Entity e, String iid) {
        if (taskAllocator instanceof FifoTaskDispatcher dispatcher) {
            dispatcher.markPartCompleted(iid, e.getId());
        }
    }

    private void completeInstruction(long now, String iid) {
        if (iid != null && instructions.containsKey(iid)) {
            Instruction inst = instructions.get(iid);
            inst.markCompleted(now);
            taskAllocator.onTaskCompleted(iid);
            System.out.println(">>> [" + now + "] 任务全流程完成: " + iid);
        }
    }

    private Location getTargetLocation(Entity e, Instruction i) {
        if (e.getType() == EntityType.QC) return gridMap.getNodeLocation(i.getDestination());
        if (e.getType() == EntityType.YC) return gridMap.getNodeLocation(i.getOrigin());
        if (e.getType() == EntityType.IT) {
            IT it = (IT) e;
            // LOAD_TO_SHIP: 没货去 Origin (找 YC), 有货去 Destination (找 QC)
            if (i.getType() == InstructionType.LOAD_TO_SHIP) {
                return it.isLoaded() ? gridMap.getNodeLocation(i.getDestination()) : gridMap.getNodeLocation(i.getOrigin());
            }
        }
        return e.getCurrentLocation();
    }

    private void bindInstruction(Entity e, Instruction i) {
        instructions.put(i.getInstructionId(), i);
        e.setCurrentInstructionId(i.getInstructionId());
        if ("PENDING".equals(i.getStatus())) {
            i.setStatus("ASSIGNED");
        }
    }
}