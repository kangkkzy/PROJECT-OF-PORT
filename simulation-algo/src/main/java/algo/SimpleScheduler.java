package algo; // 必须与文件夹名为 algo 对应

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

        decisionModule.releaseInstruction(instructionId);
        instruction.markCompleted();
        qc.setCurrentInstructionId(null);

        Instruction nextInstruction = decisionModule.getNextInstruction(qc);

        if (nextInstruction != null) {
            String currentBay = qc.getCurrentPosition();
            String nextBay = nextInstruction.getOrigin();

            if (currentBay.equals(nextBay)) {
                String targetIT = nextInstruction.getTargetIT();
                Entity it = entities.get(targetIT);

                if (it != null && it.getCurrentPosition().equals(currentBay) &&
                        (it.getStatus() == EntityStatus.WAITING || it.getStatus() == EntityStatus.IDLE)) {
                    long executionTime = timeModule.estimateExecutionTime(qc, "EXECUTE");
                    long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");

                    eventQueue.add(new SimEvent(currentTime + Math.max(executionTime, itOccupationTime),
                            EventType.IT_EXECUTION_COMPLETE, targetIT, nextInstruction.getInstructionId()));
                    eventQueue.add(new SimEvent(currentTime + executionTime,
                            EventType.QC_EXECUTION_COMPLETE, qcId, nextInstruction.getInstructionId()));

                    qc.setStatus(EntityStatus.EXECUTING);
                    it.setStatus(EntityStatus.EXECUTING);
                    nextInstruction.markInProgress();
                    qc.setCurrentInstructionId(nextInstruction.getInstructionId());
                    it.setCurrentInstructionId(nextInstruction.getInstructionId());
                } else {
                    qc.setStatus(EntityStatus.WAITING);
                    qc.setCurrentInstructionId(nextInstruction.getInstructionId());
                }
            } else {
                qc.setStatus(EntityStatus.MOVING);
                qc.setCurrentInstructionId(nextInstruction.getInstructionId());
                long moveTime = timeModule.estimateMovementTime(qc, currentBay, nextBay);
                eventQueue.add(new SimEvent(currentTime + moveTime, EventType.QC_ARRIVAL, qcId, nextInstruction.getInstructionId(), nextBay));
            }
        } else {
            qc.setStatus(EntityStatus.IDLE);
        }
    }

    // 4.2 龙门吊执行状态结束 (修复：补充缺失逻辑)
    public void handleYCExecutionComplete(long currentTime, String ycId, String instructionId) {
        Entity yc = entities.get(ycId);
        if (yc == null || yc.getType() != EntityType.YC) return;

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        decisionModule.releaseInstruction(instructionId);
        instruction.markCompleted();
        yc.setCurrentInstructionId(null);

        Instruction nextInstruction = decisionModule.getNextInstruction(yc);

        if (nextInstruction != null) {
            String currentPos = yc.getCurrentPosition();
            String nextPos = nextInstruction.getOrigin();

            if (currentPos.equals(nextPos)) {
                String targetIT = nextInstruction.getTargetIT();
                Entity it = entities.get(targetIT);

                if (it != null && it.getCurrentPosition().equals(currentPos) &&
                        (it.getStatus() == EntityStatus.WAITING || it.getStatus() == EntityStatus.IDLE)) {
                    long executionTime = timeModule.estimateExecutionTime(yc, "EXECUTE");
                    long itOccupationTime = timeModule.estimateExecutionTime(it, "OCCUPY");

                    eventQueue.add(new SimEvent(currentTime + Math.max(executionTime, itOccupationTime),
                            EventType.IT_EXECUTION_COMPLETE, targetIT, nextInstruction.getInstructionId()));
                    eventQueue.add(new SimEvent(currentTime + executionTime,
                            EventType.YC_EXECUTION_COMPLETE, ycId, nextInstruction.getInstructionId()));

                    yc.setStatus(EntityStatus.EXECUTING);
                    it.setStatus(EntityStatus.EXECUTING);
                    nextInstruction.markInProgress();
                    yc.setCurrentInstructionId(nextInstruction.getInstructionId());
                    it.setCurrentInstructionId(nextInstruction.getInstructionId());
                } else {
                    yc.setStatus(EntityStatus.WAITING);
                    yc.setCurrentInstructionId(nextInstruction.getInstructionId());
                }
            } else {
                yc.setStatus(EntityStatus.MOVING);
                yc.setCurrentInstructionId(nextInstruction.getInstructionId());
                long moveTime = timeModule.estimateMovementTime(yc, currentPos, nextPos);
                eventQueue.add(new SimEvent(currentTime + moveTime, EventType.YC_ARRIVAL, ycId, nextInstruction.getInstructionId(), nextPos));
            }
        } else {
            yc.setStatus(EntityStatus.IDLE);
        }
    }

    // 4.3 集卡执行状态结束
    public void handleITExecutionComplete(long currentTime, String itId, String instructionId) {
        Entity it = entities.get(itId);
        if (it == null || it.getType() != EntityType.IT) return;

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        decisionModule.releaseInstruction(instructionId);
        instruction.markCompleted();
        it.setCurrentInstructionId(null);

        Instruction nextInstruction = decisionModule.getNextInstruction(it);

        if (nextInstruction != null) {
            it.setStatus(EntityStatus.MOVING);
            it.setCurrentInstructionId(nextInstruction.getInstructionId());

            String targetPosition = nextInstruction.getDestination();
            long moveTime = timeModule.estimateMovementTime(it, it.getCurrentPosition(), targetPosition);

            eventQueue.add(new SimEvent(currentTime + moveTime, EventType.IT_ARRIVAL, itId, nextInstruction.getInstructionId(), targetPosition));
        } else {
            it.setStatus(EntityStatus.IDLE);
        }
    }

    // 4.4 桥吊到达
    public void handleQCArrival(long currentTime, String qcId, String instructionId, String targetPosition) {
        Entity qc = entities.get(qcId);
        if (qc == null || qc.getType() != EntityType.QC) return;

        qc.setCurrentPosition(targetPosition);
        qc.setStatus(EntityStatus.IDLE);

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        String targetIT = instruction.getTargetIT();
        Entity it = entities.get(targetIT);

        if (it != null && it.getCurrentPosition().equals(targetPosition) &&
                (it.getStatus() == EntityStatus.WAITING || it.getStatus() == EntityStatus.IDLE)) {
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

    // 4.5 龙门吊到达 (修复：补充缺失逻辑)
    public void handleYCArrival(long currentTime, String ycId, String instructionId, String targetPosition) {
        Entity yc = entities.get(ycId);
        if (yc == null || yc.getType() != EntityType.YC) return;

        yc.setCurrentPosition(targetPosition);
        yc.setStatus(EntityStatus.IDLE);

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

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

    // 4.6 集卡到达
    public void handleITArrival(long currentTime, String itId, String instructionId, String targetPosition) {
        Entity it = entities.get(itId);
        if (it == null || it.getType() != EntityType.IT) return;

        it.setCurrentPosition(targetPosition);

        Instruction instruction = instructions.get(instructionId);
        if (instruction == null) return;

        if (targetPosition.contains("QUAY") || targetPosition.contains("泊位")) {
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
        } else if (targetPosition.contains("BAY") || targetPosition.contains("贝") || targetPosition.contains("PARK")) {
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

    public Map<String, Entity> getEntities() { return entities; }
    public Map<String, Instruction> getInstructions() { return instructions; }
    public int getEventQueueSize() { return eventQueue.size(); }
}