package algo;

import entity.Entity;
import entity.EntityStatus;
import entity.EntityType;
import Instruction.Instruction;
import decision.ExternalTaskService; // 引入接口
import time.TimeEstimationModule;    // 保留这个！
import event.EventType;
import event.SimEvent;

import java.util.*;

public class SimpleScheduler {
    private ExternalTaskService taskService; // 接口化
    private TimeEstimationModule timeModule; // 物理计算保留
    private Map<String, Entity> entities;
    private Map<String, Instruction> instructions; // 本地指令缓存
    private PriorityQueue<SimEvent> eventQueue;

    // 构造函数注入接口
    public SimpleScheduler(ExternalTaskService taskService, TimeEstimationModule timeModule) {
        this.taskService = taskService;
        this.timeModule = timeModule;
        this.entities = new HashMap<>();
        this.instructions = new HashMap<>();
        this.eventQueue = new PriorityQueue<>();
    }

    public void registerEntity(Entity entity) {
        entities.put(entity.getId(), entity);
    }

    public void addInstruction(Instruction instruction) {
        instructions.put(instruction.getInstructionId(), instruction);
        taskService.submitTask(instruction); // 转发给外部/本地引擎
    }

    public void addEvent(SimEvent event) { eventQueue.add(event); }
    public SimEvent getNextEvent() { return eventQueue.poll(); }
    public boolean hasEvents() { return !eventQueue.isEmpty(); }

    // --- 核心逻辑修改示例：桥吊完成 ---
    public void handleQCExecutionComplete(long currentTime, String qcId, String instructionId) {
        Entity qc = entities.get(qcId);
        if (qc == null) return;

        // 1. 上报任务完成
        if (instructionId != null) {
            Instruction finishedInst = instructions.get(instructionId);
            if (finishedInst != null) finishedInst.markCompleted();
            taskService.reportTaskCompletion(instructionId, qcId);
        }
        qc.setCurrentInstructionId(null);

        // 2. 请求新任务 (Ask)
        Instruction nextInstruction = taskService.askForNewTask(qc);

        if (nextInstruction != null) {
            // 缓存指令信息
            if (!instructions.containsKey(nextInstruction.getInstructionId())) {
                instructions.put(nextInstruction.getInstructionId(), nextInstruction);
            }

            // 3. 执行物理调度逻辑 (Execute)
            processNextInstruction(currentTime, qc, nextInstruction);
        } else {
            qc.setStatus(EntityStatus.IDLE);
        }
    }

    // --- 核心逻辑修改示例：龙门吊完成 ---
    public void handleYCExecutionComplete(long currentTime, String ycId, String instructionId) {
        Entity yc = entities.get(ycId);
        if (yc == null) return;

        if (instructionId != null) {
            Instruction finishedInst = instructions.get(instructionId);
            if (finishedInst != null) finishedInst.markCompleted();
            taskService.reportTaskCompletion(instructionId, ycId);
        }
        yc.setCurrentInstructionId(null);

        Instruction nextInstruction = taskService.askForNewTask(yc);

        if (nextInstruction != null) {
            if (!instructions.containsKey(nextInstruction.getInstructionId())) {
                instructions.put(nextInstruction.getInstructionId(), nextInstruction);
            }
            processNextInstruction(currentTime, yc, nextInstruction);
        } else {
            yc.setStatus(EntityStatus.IDLE);
        }
    }

    // --- 核心逻辑修改示例：集卡完成 ---
    public void handleITExecutionComplete(long currentTime, String itId, String instructionId) {
        Entity it = entities.get(itId);
        if (it == null) return;

        if (instructionId != null) {
            Instruction finishedInst = instructions.get(instructionId);
            if (finishedInst != null) finishedInst.markCompleted();
            taskService.reportTaskCompletion(instructionId, itId);
        }
        it.setCurrentInstructionId(null);

        Instruction nextInstruction = taskService.askForNewTask(it);

        if (nextInstruction != null) {
            it.setStatus(EntityStatus.MOVING);
            it.setCurrentInstructionId(nextInstruction.getInstructionId());
            // 使用 TimeEstimationModule 计算时间
            long moveTime = timeModule.estimateMovementTime(it, it.getCurrentPosition(), nextInstruction.getDestination());
            eventQueue.add(new SimEvent(currentTime + moveTime, EventType.IT_ARRIVAL, itId, nextInstruction.getInstructionId(), nextInstruction.getDestination()));
        } else {
            it.setStatus(EntityStatus.IDLE);
        }
    }

    // --- 辅助方法：统一处理 QC/YC 的移动与握手逻辑 ---
    private void processNextInstruction(long currentTime, Entity crane, Instruction nextInstruction) {
        String currentPos = crane.getCurrentPosition();
        String nextPos = nextInstruction.getOrigin();

        if (currentPos.equals(nextPos)) {
            // 在原位，检查集卡
            String targetIT = nextInstruction.getTargetIT();
            Entity it = entities.get(targetIT);

            if (it != null && it.getCurrentPosition().equals(currentPos) &&
                    (it.getStatus() == EntityStatus.WAITING || it.getStatus() == EntityStatus.IDLE)) {
                // 开始作业
                long executionTime = timeModule.estimateExecutionTime(crane, "EXECUTE");
                long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");

                EventType completeEvent = (crane.getType() == EntityType.QC) ? EventType.QC_EXECUTION_COMPLETE : EventType.YC_EXECUTION_COMPLETE;

                eventQueue.add(new SimEvent(currentTime + Math.max(executionTime, itOccupationTime),
                        EventType.IT_EXECUTION_COMPLETE, targetIT, nextInstruction.getInstructionId()));
                eventQueue.add(new SimEvent(currentTime + executionTime,
                        completeEvent, crane.getId(), nextInstruction.getInstructionId()));

                crane.setStatus(EntityStatus.EXECUTING);
                it.setStatus(EntityStatus.EXECUTING);
                nextInstruction.markInProgress();
                crane.setCurrentInstructionId(nextInstruction.getInstructionId());
                it.setCurrentInstructionId(nextInstruction.getInstructionId());
            } else {
                crane.setStatus(EntityStatus.WAITING);
                crane.setCurrentInstructionId(nextInstruction.getInstructionId());
            }
        } else {
            // 需要移动
            crane.setStatus(EntityStatus.MOVING);
            crane.setCurrentInstructionId(nextInstruction.getInstructionId());
            long moveTime = timeModule.estimateMovementTime(crane, currentPos, nextPos);

            EventType arrivalEvent = (crane.getType() == EntityType.QC) ? EventType.QC_ARRIVAL : EventType.YC_ARRIVAL;
            eventQueue.add(new SimEvent(currentTime + moveTime, arrivalEvent, crane.getId(), nextInstruction.getInstructionId(), nextPos));
        }
    }

    // 保留 handleQCArrival, handleITArrival 等其他方法，逻辑不变（略）
    // 只需要确保它们内部不直接调用 decisionModule 即可（原来的 Arrival 逻辑大多只涉及状态更新，不涉及索取新任务，所以改动较小）
    public void handleQCArrival(long currentTime, String qcId, String instructionId, String targetPosition) {
        // ...原代码逻辑，如果有用到 decisionModule 则替换，通常 Arrival 不需要索取新任务...
        // 为了完整性，这里简略展示，只需保持原逻辑的握手部分即可。
        Entity qc = entities.get(qcId);
        if (qc == null) return;
        qc.setCurrentPosition(targetPosition);
        qc.setStatus(EntityStatus.IDLE);

        Instruction instruction = instructions.get(instructionId);
        if (instruction != null) {
            String targetIT = instruction.getTargetIT();
            Entity it = entities.get(targetIT);
            // ... 握手逻辑与之前相同，直接复制原代码 ...
            if (it != null && it.getCurrentPosition().equals(targetPosition) &&
                    (it.getStatus() == EntityStatus.WAITING || it.getStatus() == EntityStatus.IDLE)) {
                // 生成 EXECUTION 事件
                long executionTime = timeModule.estimateExecutionTime(qc, "EXECUTE");
                long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");
                eventQueue.add(new SimEvent(currentTime + Math.max(executionTime, itOccupationTime),
                        EventType.IT_EXECUTION_COMPLETE, targetIT, instructionId));
                eventQueue.add(new SimEvent(currentTime + executionTime,
                        EventType.QC_EXECUTION_COMPLETE, qcId, instructionId));
                qc.setStatus(EntityStatus.EXECUTING);
                it.setStatus(EntityStatus.EXECUTING);
                instruction.markInProgress();
            } else {
                qc.setStatus(EntityStatus.WAITING);
            }
        }
    }

    // handleYCArrival 和 handleITArrival 同上，保持原样即可
    public void handleYCArrival(long currentTime, String ycId, String instructionId, String targetPosition) {
        // ... 复用原代码 ...
        Entity yc = entities.get(ycId);
        if (yc == null) return;
        yc.setCurrentPosition(targetPosition);
        yc.setStatus(EntityStatus.IDLE);

        Instruction instruction = instructions.get(instructionId);
        if (instruction != null) {
            String targetIT = instruction.getTargetIT();
            Entity it = entities.get(targetIT);
            if (it != null && it.getCurrentPosition().equals(targetPosition) &&
                    (it.getStatus() == EntityStatus.WAITING || it.getStatus() == EntityStatus.IDLE)) {
                long executionTime = timeModule.estimateExecutionTime(yc, "EXECUTE");
                long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");
                eventQueue.add(new SimEvent(currentTime + Math.max(executionTime, itOccupationTime),
                        EventType.IT_EXECUTION_COMPLETE, targetIT, instructionId));
                eventQueue.add(new SimEvent(currentTime + executionTime,
                        EventType.YC_EXECUTION_COMPLETE, ycId, instructionId));
                yc.setStatus(EntityStatus.EXECUTING);
                it.setStatus(EntityStatus.EXECUTING);
                instruction.markInProgress();
            } else {
                yc.setStatus(EntityStatus.WAITING);
            }
        }
    }

    public void handleITArrival(long currentTime, String itId, String instructionId, String targetPosition) {
        // ... 复用原代码 ...
        Entity it = entities.get(itId);
        if (it == null) return;
        it.setCurrentPosition(targetPosition);

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        if (targetPosition.contains("QUAY")) {
            String targetQC = instruction.getTargetQC();
            Entity qc = entities.get(targetQC);
            if (qc != null && qc.getCurrentPosition().equals(targetPosition) && qc.getStatus() == EntityStatus.WAITING) {
                long executionTime = timeModule.estimateExecutionTime(qc, "EXECUTE");
                long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");
                eventQueue.add(new SimEvent(currentTime + Math.max(executionTime, itOccupationTime),
                        EventType.IT_EXECUTION_COMPLETE, itId, instructionId));
                eventQueue.add(new SimEvent(currentTime + executionTime,
                        EventType.QC_EXECUTION_COMPLETE, targetQC, instructionId));
                qc.setStatus(EntityStatus.EXECUTING);
                it.setStatus(EntityStatus.EXECUTING);
                instruction.markInProgress();
            } else {
                it.setStatus(EntityStatus.WAITING);
            }
        } else if (targetPosition.contains("BAY") || targetPosition.contains("PARK")) {
            String targetYC = instruction.getTargetYC();
            Entity yc = entities.get(targetYC);
            if (yc != null && yc.getCurrentPosition().equals(targetPosition) && yc.getStatus() == EntityStatus.WAITING) {
                long executionTime = timeModule.estimateExecutionTime(yc, "EXECUTE");
                long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");
                eventQueue.add(new SimEvent(currentTime + Math.max(executionTime, itOccupationTime),
                        EventType.IT_EXECUTION_COMPLETE, itId, instructionId));
                eventQueue.add(new SimEvent(currentTime + executionTime,
                        EventType.YC_EXECUTION_COMPLETE, targetYC, instructionId));
                yc.setStatus(EntityStatus.EXECUTING);
                it.setStatus(EntityStatus.EXECUTING);
                instruction.markInProgress();
            } else {
                it.setStatus(EntityStatus.WAITING);
            }
        }
    }
}