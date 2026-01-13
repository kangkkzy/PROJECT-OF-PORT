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
    private PortMap portMap;

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

    public void handleExecutionComplete(long currentTime, String entityId, String instructionId) {
        // 【新增逻辑】: 任务完成或到达，必须立刻释放之前占用的路段资源！
        physicsEngine.unlockResources(entityId);

        Entity entity = entities.get(entityId);
        if (entity == null) return;

        //  上报完成
        if (instructionId != null) {
            Instruction inst = instructions.get(instructionId);
            if (inst != null) {
                inst.markCompleted();
                taskService.reportTaskCompletion(instructionId, entityId);
            }
        }
        entity.setCurrentInstructionId(null);
        entity.setStatus(EntityStatus.IDLE);

        // 索取新任务
        Instruction nextInst = taskService.askForNewTask(entity);
        if (nextInst != null) {
            executeInstruction(currentTime, entity, nextInst);
        }
    }

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
            handleOperationInstruction(currentTime, entity, instruction);
        }
    }

    private void handleMoveInstruction(long currentTime, Entity entity, Instruction instruction) {
        String from = entity.getCurrentPosition();
        String to = instruction.getDestination();

        //  获取路径 (外部
        List<String> routeSegments = taskService.getRoute(from, to);

        //   碰撞检测 (内部
        boolean hasCollision = false;
        String collidedSegment = null;
        if (routeSegments != null) {
            for (String segId : routeSegments) {
                // 现在这里的 detectCollision 会真正去查表了
                if (physicsEngine.detectCollision(segId, entity.getId())) {
                    hasCollision = true;
                    collidedSegment = segId;
                    break;
                }
            }
        }

        //  冲突处理 (外部
        if (hasCollision) {
            Instruction recoveryInstruction = taskService.askForCollisionSolution(entity.getId(), collidedSegment);
            if (recoveryInstruction != null) {
                executeInstruction(currentTime, entity, recoveryInstruction);
            } else {
                System.err.println("CRITICAL: 算法未解决冲突，实体挂起: " + entity.getId());
                entity.setStatus(EntityStatus.IDLE);
            }
            return;
        }

        // 4. 执行移动
        physicsEngine.lockSegments(entity.getId(), routeSegments);

        long duration = timeModule.estimateMovementTime(entity, routeSegments);
        entity.setStatus(EntityStatus.MOVING);
        EventType type = getArrivalType(entity);
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), instruction.getInstructionId(), to));
    }

    private void handleOperationInstruction(long currentTime, Entity entity, Instruction instruction) {
        // 作业指令不锁定
        long duration = instruction.getExpectedDuration();
        if (duration <= 0) System.err.println("警告: 指令耗时为0 " + instruction.getInstructionId());

        entity.setStatus(EntityStatus.EXECUTING);
        EventType type = getCompleteType(entity);
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), instruction.getInstructionId()));
    }

    private void handleWaitInstruction(long currentTime, Entity entity, Instruction instruction) {
        long duration = instruction.getExpectedDuration();
// 等待不锁
        entity.setStatus(EntityStatus.WAITING);
        EventType type = getCompleteType(entity);
        eventQueue.add(new SimEvent(currentTime + duration, type, entity.getId(), instruction.getInstructionId()));
    }

    // 事件映射
    public void handleITArrival(long t, String id, String iId, String p) { Entity e=entities.get(id); if(e!=null)e.setCurrentPosition(p); handleExecutionComplete(t, id, iId); }
    public void handleITExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }
    public void handleQCArrival(long t, String id, String iId, String p) { handleITArrival(t, id, iId, p); }
    public void handleQCExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }
    public void handleYCArrival(long t, String id, String iId, String p) { handleITArrival(t, id, iId, p); }
    public void handleYCExecutionComplete(long t, String id, String iId) { handleExecutionComplete(t, id, iId); }

    private EventType getArrivalType(Entity e) { switch(e.getType()){case QC:return EventType.QC_ARRIVAL;case YC:return EventType.YC_ARRIVAL;default:return EventType.IT_ARRIVAL;}}
    private EventType getCompleteType(Entity e) { switch(e.getType()){case QC:return EventType.QC_EXECUTION_COMPLETE;case YC:return EventType.YC_EXECUTION_COMPLETE;default:return EventType.IT_EXECUTION_COMPLETE;}}
}