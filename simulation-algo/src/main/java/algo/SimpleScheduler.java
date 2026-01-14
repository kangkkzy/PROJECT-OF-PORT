package algo;

import entity.Entity;
import entity.EntityStatus;
import Instruction.Instruction;
import Instruction.InstructionType;
import decision.ExternalTaskService;
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
    private GridMap gridMap; // 替换了 PortMap

    private Map<String, Entity> entities;
    private Map<String, Instruction> instructions;
    private PriorityQueue<SimEvent> eventQueue;

    public SimpleScheduler(ExternalTaskService taskService,
                           TimeEstimationModule timeModule,
                           PhysicsEngine physicsEngine,
                           GridMap gridMap) {
        this.taskService = taskService;
        this.timeModule = timeModule;
        this.physicsEngine = physicsEngine;
        this.gridMap = gridMap;
        this.entities = new HashMap<>();
        this.instructions = new HashMap<>();
        this.eventQueue = new PriorityQueue<>();
    }

    public void registerEntity(Entity entity) {
        entities.put(entity.getId(), entity);
        // 初始化时，如果实体的 currentPosition 是 NodeID (如 "QUAY_01")
        // 需要将其转换为 Grid Key (如 "10_10")
        String gridPos = gridMap.getNodePosition(entity.getCurrentPosition());
        if (gridPos != null) {
            entity.setCurrentPosition(gridPos);
        }
        // 初始位置锁定
        physicsEngine.lockResources(entity.getId(), Collections.singletonList(entity.getCurrentPosition()));
    }

    public void addInstruction(Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        taskService.submitTask(instruction);
    }

    public SimEvent getNextEvent() { return eventQueue.poll(); }

    public void init() {
        System.out.println("正在初始化仿真事件(Grid模式)...");
        for (Entity entity : entities.values()) {
            if (entity.getStatus() == EntityStatus.IDLE) {
                Instruction inst = taskService.askForNewTask(entity);
                if (inst != null) {
                    executeInstruction(0, entity, inst);
                }
            }
        }
    }

    public void handleExecutionComplete(long currentTime, String entityId, String instructionId) {
        // 完成任务时不释放当前占用的格子 只更改状态
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
        if (nextInst != null) {
            executeInstruction(currentTime, entity, nextInst);
        }
    }

    private void executeInstruction(long currentTime, Entity entity, Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        entity.setCurrentInstructionId(instruction.getInstructionId());
        instruction.markInProgress();

        if (instruction.getType() == InstructionType.MOVE || instruction.getType() == InstructionType.LOAD_TO_SHIP) {
            // 所有涉及移动的指令都走移动逻辑
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
            handleExecutionComplete(currentTime, entity.getId(), instruction.getInstructionId());
            return;
        }

        entity.setRemainingPath(routeCells);
        processNextMovementStep(currentTime, entity);
    }

    // Grid 模式下的核心步进逻辑
    private void processNextMovementStep(long currentTime, Entity entity) {
        if (!entity.hasRemainingPath()) {
            handleExecutionComplete(currentTime, entity.getId(), entity.getCurrentInstructionId());
            return;
        }

        // 中断检查
        Instruction interruptInst = taskService.askForInterruption(entity);
        if (interruptInst != null) {
            entity.setRemainingPath(null);
            executeInstruction(currentTime, entity, interruptInst);
            return;
        }

        // 获取下一个格子
        String nextPosKey = entity.getRemainingPath().get(0);

        // 碰撞检测
        if (physicsEngine.detectCollision(nextPosKey, entity.getId())) {
            Instruction recovery = taskService.askForCollisionSolution(entity.getId(), nextPosKey);
            if (recovery != null) {
                executeInstruction(currentTime, entity, recovery);
            } else {
                entity.setStatus(EntityStatus.WAITING);
            }
            return;
        }

        // 锁定下一个格子
        physicsEngine.lockResources(entity.getId(), Collections.singletonList(nextPosKey));

        // 计算移动时间 (只移动一格)
        long duration = timeModule.estimateMovementTime(entity, Collections.singletonList(nextPosKey));

        entity.setStatus(EntityStatus.MOVING);
        EventType type = getArrivalType(entity);

        // 添加事件
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), entity.getCurrentInstructionId(), nextPosKey));
    }

    // 处理到达事件
    public void handleITArrival(long t, String id, String iId, String p) { handleCellArrival(t, id, iId, p); }
    public void handleQCArrival(long t, String id, String iId, String p) { handleCellArrival(t, id, iId, p); }
    public void handleYCArrival(long t, String id, String iId, String p) { handleCellArrival(t, id, iId, p); }

    private void handleCellArrival(long currentTime, String entityId, String instructionId, String targetPosKey) {
        Entity entity = entities.get(entityId);
        if (entity == null) return;

        // 释放上一个位置的锁
        String oldPos = entity.getCurrentPosition();
        if (oldPos != null && !oldPos.equals(targetPosKey)) {
            physicsEngine.unlockSingleResource(entityId, oldPos);
        }

        // 更新位置
        entity.setCurrentPosition(targetPosKey);

        // 核心修改：调用 popNextStep() 替代 popNextSegment()
        entity.popNextStep(); // 移除已到达的格子

        // 继续走
        processNextMovementStep(currentTime, entity);
    }

    private void handleOperationInstruction(long currentTime, Entity entity, Instruction instruction) {
        long duration = instruction.getExpectedDuration();
        if (duration <= 0) duration = 1000;
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