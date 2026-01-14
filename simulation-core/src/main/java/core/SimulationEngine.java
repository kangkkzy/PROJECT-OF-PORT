package core;

import algo.SimpleScheduler;
import entity.Entity;
import event.*;
import Instruction.*;
import java.util.*;

public class SimulationEngine {
    private long currentTime;
    private PriorityQueue<SimEvent> eventQueue;
    private SimulationConfig config;
    private boolean isRunning;
    private SimpleScheduler scheduler;
    private List<SimEvent> eventLog;

    public SimulationEngine(SimulationConfig config, SimpleScheduler scheduler) {
        this.config = config;
        this.scheduler = scheduler;
        this.currentTime = 0;
        this.eventQueue = new PriorityQueue<>();
        this.eventLog = new ArrayList<>();
        this.isRunning = false;
    }

    public void addEntity(Entity entity) { scheduler.registerEntity(entity); }
    public void addInstruction(Instruction instruction) { scheduler.addInstruction(instruction); }

    // 【新增】暴露事件日志供外部保存
    public List<SimEvent> getEventLog() {
        return eventLog;
    }

    private void processEvent(SimEvent event) {
        switch (event.getType()) {
            case TASK_GENERATION:
                scheduler.handleTaskGeneration(currentTime);
                break;
            case MOVE_STEP:
                scheduler.handleStepArrival(currentTime, event.getEntityId(), event.getInstructionId(), event.getTargetPosition());
                break;
            case QC_EXECUTION_COMPLETE:
                scheduler.handleQCExecutionComplete(currentTime, event.getEntityId(), event.getInstructionId());
                break;
            case QC_ARRIVAL:
                scheduler.handleQCArrival(currentTime, event.getEntityId(), event.getInstructionId(), event.getTargetPosition());
                break;
            case YC_EXECUTION_COMPLETE:
                scheduler.handleYCExecutionComplete(currentTime, event.getEntityId(), event.getInstructionId());
                break;
            case YC_ARRIVAL:
                scheduler.handleYCArrival(currentTime, event.getEntityId(), event.getInstructionId(), event.getTargetPosition());
                break;
            case IT_EXECUTION_COMPLETE:
                scheduler.handleITExecutionComplete(currentTime, event.getEntityId(), event.getInstructionId());
                break;
            case IT_ARRIVAL:
                scheduler.handleITArrival(currentTime, event.getEntityId(), event.getInstructionId(), event.getTargetPosition());
                break;
            default:
                System.err.println("未知事件类型: " + event.getType());
        }
    }

    public void generateReport() {
        System.out.println("=== 仿真报告 ===");
        System.out.println("总运行时长: " + currentTime + "ms");
        long meaningfulCount = eventLog.stream().filter(e -> e.getType() != EventType.MOVE_STEP).count();
        System.out.println("处理关键业务事件数: " + meaningfulCount);
    }

    public void start() {
        if (isRunning) throw new IllegalStateException("仿真已经在运行中");
        isRunning = true;
        System.out.println("仿真开始...");

        long maxSimulationTime = config.getSimulationDuration();
        int processedEvents = 0;

        while (isRunning && currentTime < maxSimulationTime && (!eventQueue.isEmpty() || scheduler.hasPendingEvents())) {
            SimEvent event = scheduler.getNextEvent();
            if (event == null) event = this.eventQueue.poll();
            if (event == null) break;

            if (event.getTimestamp() >= currentTime) currentTime = event.getTimestamp();

            // 只打印关键业务事件
            if (event.getType() != EventType.MOVE_STEP && event.getType() != EventType.TASK_GENERATION) {
                System.out.println(">> 处理事件: " + event);
            }

            processEvent(event);
            eventLog.add(event);
            processedEvents++;

            if (processedEvents >= config.getMaxEvents()) {
                System.out.println("达到最大事件数限制，停止仿真");
                break;
            }
        }
        System.out.println("仿真结束!");
    }

    public static class SimulationConfig {
        private String name;
        private long simulationDuration;
        private long timeStep;
        private int maxEvents;
        public long getSimulationDuration() { return simulationDuration; }
        public void setSimulationDuration(long simulationDuration) { this.simulationDuration = simulationDuration; }
        public void setMaxEvents(int maxEvents) { this.maxEvents = maxEvents; }
        public int getMaxEvents() { return maxEvents; }
        public void setTimeStep(long timeStep) { this.timeStep = timeStep; }
        public void setName(String name) { this.name = name; }
    }
}