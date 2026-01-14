package core;

import algo.SimpleScheduler;
import entity.Entity; // 【关键修改】添加这一行
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

    public void addEntity(Entity entity) {
        scheduler.registerEntity(entity);
    }

    public void addInstruction(Instruction instruction) {
        scheduler.addInstruction(instruction);
    }

    private void processEvent(SimEvent event) {
        EventType type = event.getType();
        String entityId = event.getEntityId();
        String instructionId = event.getInstructionId();

        // 委托给 Scheduler
        switch (type) {
            case QC_EXECUTION_COMPLETE:
                scheduler.handleQCExecutionComplete(currentTime, entityId, instructionId);
                break;
            case QC_ARRIVAL:
                scheduler.handleQCArrival(currentTime, entityId, instructionId, event.getTargetPosition());
                break;
            case YC_EXECUTION_COMPLETE:
                scheduler.handleYCExecutionComplete(currentTime, entityId, instructionId);
                break;
            case YC_ARRIVAL:
                scheduler.handleYCArrival(currentTime, entityId, instructionId, event.getTargetPosition());
                break;
            case IT_EXECUTION_COMPLETE:
                scheduler.handleITExecutionComplete(currentTime, entityId, instructionId);
                break;
            case IT_ARRIVAL:
                scheduler.handleITArrival(currentTime, entityId, instructionId, event.getTargetPosition());
                break;
            default:
                System.err.println("未知事件类型: " + type);
        }
    }

    public void generateReport() {
        System.out.println("=== 仿真报告 ===");
        System.out.println("总运行时长: " + currentTime + "ms");
        System.out.println("处理事件数: " + eventLog.size());
    }

    public void start() {
        if (isRunning) throw new IllegalStateException("仿真已经在运行中");
        isRunning = true;
        System.out.println("仿真开始...");

        long maxSimulationTime = config.getSimulationDuration();
        int processedEvents = 0;

        while (isRunning && currentTime < maxSimulationTime && !eventQueue.isEmpty()) {
            SimEvent event = scheduler.getNextEvent();
            if (event == null) event = this.eventQueue.poll();
            if (event == null) break;

            currentTime = event.getTimestamp();
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