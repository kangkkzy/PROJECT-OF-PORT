import entity.Entity;
import entity.EntityStatus;
import entity.EntityType;
import Instruction.Instruction;
import decision.DecisionModule;
import time.TimeEstimationModule;
import event.EventType;
import event.SimEvent;

import java.util.*;

public class SimpleScheduler {
    private DecisionModule decisionModule;
    private TimeEstimationModule timeModule;
    private Map<String, Entity> entities;
    private Map<String, Instruction> instructions;
    private PriorityQueue<SimEvent> eventQueue;

    public SimpleScheduler(DecisionModule decisionModule, TimeEstimationModule timeModule) {
        this.decisionModule = decisionModule;
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
        decisionModule.addInstruction(instruction);
    }

    public void addEvent(SimEvent event) {
        eventQueue.add(event);
    }

    public SimEvent getNextEvent() {
        return eventQueue.poll();
    }

    public boolean hasEvents() {
        return !eventQueue.isEmpty();
    }

    // 4.1 桥吊执行状态结束事件处理
    public void handleQCExecutionComplete(long currentTime, String qcId, String instructionId) {
        Entity qc = entities.get(qcId);
        if (qc == null || qc.getType() != EntityType.QC) return;

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        // 释放指令
        decisionModule.releaseInstruction(instructionId);
        instruction.markCompleted();
        qc.setCurrentInstructionId(null);

        // 从决策模块获取下一个指令
        Instruction nextInstruction = decisionModule.getNextInstruction(qc);

        if (nextInstruction != null) {
            String currentBay = qc.getCurrentPosition();
            String nextBay = nextInstruction.getOrigin();

            if (currentBay.equals(nextBay)) {
                // 仍在当前贝位
                String targetIT = nextInstruction.getTargetIT();
                Entity it = entities.get(targetIT);

                if (it != null && it.getCurrentPosition().equals(currentBay) &&
                        (it.getStatus() == EntityStatus.WAITING || it.getStatus() == EntityStatus.IDLE)) {
                    // 集卡已经到达
                    long executionTime = timeModule.estimateExecutionTime(qc, "EXECUTE");
                    long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");

                    // 生成集卡执行结束事件
                    SimEvent itCompleteEvent = new SimEvent(
                            currentTime + Math.max(executionTime, itOccupationTime),
                            EventType.IT_EXECUTION_COMPLETE,
                            targetIT,
                            nextInstruction.getInstructionId()
                    );
                    eventQueue.add(itCompleteEvent);

                    // 生成桥吊执行结束事件
                    SimEvent qcCompleteEvent = new SimEvent(
                            currentTime + executionTime,
                            EventType.QC_EXECUTION_COMPLETE,
                            qcId,
                            nextInstruction.getInstructionId()
                    );
                    eventQueue.add(qcCompleteEvent);

                    // 更新状态
                    qc.setStatus(EntityStatus.EXECUTING);
                    it.setStatus(EntityStatus.EXECUTING);
                    nextInstruction.markInProgress();
                    qc.setCurrentInstructionId(nextInstruction.getInstructionId());
                    it.setCurrentInstructionId(nextInstruction.getInstructionId());
                } else {
                    // 集卡未到达，桥吊等待
                    qc.setStatus(EntityStatus.WAITING);
                    qc.setCurrentInstructionId(nextInstruction.getInstructionId());
                }
            } else {
                // 需要移动到不同贝位
                qc.setStatus(EntityStatus.MOVING);
                qc.setCurrentInstructionId(nextInstruction.getInstructionId());

                long moveTime = timeModule.estimateMovementTime(qc, currentBay, nextBay);
                SimEvent arrivalEvent = new SimEvent(
                        currentTime + moveTime,
                        EventType.QC_ARRIVAL,
                        qcId,
                        nextInstruction.getInstructionId(),
                        nextBay
                );
                eventQueue.add(arrivalEvent);
            }
        } else {
            // 没有下一个指令，桥吊空闲
            qc.setStatus(EntityStatus.IDLE);
        }
    }

    // 4.2 集卡执行状态结束事件处理
    public void handleITExecutionComplete(long currentTime, String itId, String instructionId) {
        Entity it = entities.get(itId);
        if (it == null || it.getType() != EntityType.IT) return;

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        // 释放指令
        decisionModule.releaseInstruction(instructionId);
        instruction.markCompleted();
        it.setCurrentInstructionId(null);

        // 从决策模块获取下一个指令
        Instruction nextInstruction = decisionModule.getNextInstruction(it);

        if (nextInstruction != null) {
            // 集卡进入移动状态
            it.setStatus(EntityStatus.MOVING);
            it.setCurrentInstructionId(nextInstruction.getInstructionId());

            String targetPosition = nextInstruction.getDestination();
            long moveTime = timeModule.estimateMovementTime(it, it.getCurrentPosition(), targetPosition);

            SimEvent arrivalEvent = new SimEvent(
                    currentTime + moveTime,
                    EventType.IT_ARRIVAL,
                    itId,
                    nextInstruction.getInstructionId(),
                    targetPosition
            );
            eventQueue.add(arrivalEvent);
        } else {
            // 没有下一个指令，集卡空闲
            it.setStatus(EntityStatus.IDLE);
        }
    }

    // 4.3 桥吊到达目标位置事件处理
    public void handleQCArrival(long currentTime, String qcId, String instructionId, String targetPosition) {
        Entity qc = entities.get(qcId);
        if (qc == null || qc.getType() != EntityType.QC) return;

        qc.setCurrentPosition(targetPosition);
        qc.setStatus(EntityStatus.IDLE);

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        // 检查是否有集卡在等待
        String targetIT = instruction.getTargetIT();
        Entity it = entities.get(targetIT);

        if (it != null && it.getCurrentPosition().equals(targetPosition) &&
                (it.getStatus() == EntityStatus.WAITING || it.getStatus() == EntityStatus.IDLE)) {
            // 集卡已经到达
            long executionTime = timeModule.estimateExecutionTime(qc, "EXECUTE");
            long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");

            // 生成集卡执行结束事件
            SimEvent itCompleteEvent = new SimEvent(
                    currentTime + Math.max(executionTime, itOccupationTime),
                    EventType.IT_EXECUTION_COMPLETE,
                    targetIT,
                    instructionId
            );
            eventQueue.add(itCompleteEvent);

            // 生成桥吊执行结束事件
            SimEvent qcCompleteEvent = new SimEvent(
                    currentTime + executionTime,
                    EventType.QC_EXECUTION_COMPLETE,
                    qcId,
                    instructionId
            );
            eventQueue.add(qcCompleteEvent);

            // 更新状态
            qc.setStatus(EntityStatus.EXECUTING);
            it.setStatus(EntityStatus.EXECUTING);
            instruction.markInProgress();
        } else {
            // 集卡未到达，桥吊等待
            qc.setStatus(EntityStatus.WAITING);
        }
    }

    // 4.6 集卡到达目标位置事件处理
    public void handleITArrival(long currentTime, String itId, String instructionId, String targetPosition) {
        Entity it = entities.get(itId);
        if (it == null || it.getType() != EntityType.IT) return;

        it.setCurrentPosition(targetPosition);

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        // 判断到达位置类型
        map.Node targetNode = null; // 需要从地图获取节点信息
        // 这里简化处理，假设目标位置包含类型信息
        if (targetPosition.contains("QUAY")) {
            // 到达岸边
            String targetQC = instruction.getTargetQC();
            Entity qc = entities.get(targetQC);

            if (qc != null && qc.getCurrentPosition().equals(targetPosition) &&
                    qc.getStatus() == EntityStatus.WAITING) {
                // 桥吊在等待
                long executionTime = timeModule.estimateExecutionTime(qc, "EXECUTE");
                long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");

                // 生成集卡执行结束事件
                SimEvent itCompleteEvent = new SimEvent(
                        currentTime + Math.max(executionTime, itOccupationTime),
                        EventType.IT_EXECUTION_COMPLETE,
                        itId,
                        instructionId
                );
                eventQueue.add(itCompleteEvent);

                // 生成桥吊执行结束事件
                SimEvent qcCompleteEvent = new SimEvent(
                        currentTime + executionTime,
                        EventType.QC_EXECUTION_COMPLETE,
                        targetQC,
                        instructionId
                );
                eventQueue.add(qcCompleteEvent);

                // 更新状态
                qc.setStatus(EntityStatus.EXECUTING);
                it.setStatus(EntityStatus.EXECUTING);
                instruction.markInProgress();
            } else {
                // 桥吊在移动或执行，集卡等待
                it.setStatus(EntityStatus.WAITING);
            }
        } else if (targetPosition.contains("BAY")) {
            // 到达堆场
            String targetYC = instruction.getTargetYC();
            Entity yc = entities.get(targetYC);

            if (yc != null && yc.getCurrentPosition().equals(targetPosition) &&
                    yc.getStatus() == EntityStatus.WAITING) {
                // 龙门吊在等待
                long executionTime = timeModule.estimateExecutionTime(yc, "EXECUTE");
                long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");

                // 生成集卡执行结束事件
                SimEvent itCompleteEvent = new SimEvent(
                        currentTime + Math.max(executionTime, itOccupationTime),
                        EventType.IT_EXECUTION_COMPLETE,
                        itId,
                        instructionId
                );
                eventQueue.add(itCompleteEvent);

                // 生成龙门吊执行结束事件
                SimEvent ycCompleteEvent = new SimEvent(
                        currentTime + executionTime,
                        EventType.YC_EXECUTION_COMPLETE,
                        targetYC,
                        instructionId
                );
                eventQueue.add(ycCompleteEvent);

                // 更新状态
                yc.setStatus(EntityStatus.EXECUTING);
                it.setStatus(EntityStatus.EXECUTING);
                instruction.markInProgress();
            } else {
                // 龙门吊在移动或执行，集卡等待
                it.setStatus(EntityStatus.WAITING);
            }
        }
    }

    public Map<String, Entity> getEntities() {
        return entities;
    }

    public Map<String, Instruction> getInstructions() {
        return instructions;
    }

    public int getEventQueueSize() {
        return eventQueue.size();
    }
}
