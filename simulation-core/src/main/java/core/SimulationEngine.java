package core; // 必须与文件夹名为 core 对应

import entity.*;
import event.*;
import Instruction.*;
import map.PortMap;
import decision.DecisionModule;
import time.TimeEstimationModule;
import algo.SimpleScheduler; // 引用 algo 包下的 Scheduler
import java.util.*;

public class SimulationEngine {
    private long currentTime;
    private PriorityQueue<SimEvent> eventQueue;
    private Map<String, Instruction> instructionMap;
    private Map<String, Entity> entityMap;
    private PortMap portMap;
    private DecisionModule decisionModule;
    private TimeEstimationModule timeModule;
    private SimpleScheduler scheduler;
    private List<SimEvent> eventLog;
    private SimulationConfig config;
    private boolean isRunning;

    public SimulationEngine(PortMap portMap, SimulationConfig config) {
        this.currentTime = 0;
        this.eventQueue = new PriorityQueue<>();
        this.instructionMap = new HashMap<>();
        this.entityMap = new HashMap<>();
        this.portMap = portMap;
        this.config = config;
        this.eventLog = new ArrayList<>();
        this.isRunning = false;

        // 初始化模块
        this.decisionModule = new DecisionModule();
        this.timeModule = new TimeEstimationModule(portMap);
        // 这里需要传递两个模块给 Scheduler
        this.scheduler = new SimpleScheduler(decisionModule, timeModule);
    }

    public void addEntity(Entity entity) {
        entityMap.put(entity.getId(), entity);
        scheduler.registerEntity(entity);
    }

    public void addInstruction(Instruction instruction) {
        instructionMap.put(instruction.getInstructionId(), instruction);
        scheduler.addInstruction(instruction);
    }

    public void addEvent(SimEvent event) {
        eventQueue.add(event);
    }

    public void start() {
        if (isRunning) throw new IllegalStateException("仿真已经在运行中");

        isRunning = true;
        System.out.println("仿真开始...");

        long startRealTime = System.currentTimeMillis();
        long maxSimulationTime = config.getSimulationDuration();
        int processedEvents = 0;

        while (isRunning && currentTime < maxSimulationTime && !eventQueue.isEmpty()) {
            SimEvent event = eventQueue.poll();
            if (event == null) break;

            currentTime = event.getTimestamp();
            processEvent(event);
            eventLog.add(event);
            processedEvents++;

            if (processedEvents >= config.getMaxEvents()) {
                System.out.println("达到最大事件数量限制: " + config.getMaxEvents());
                break;
            }

            if (processedEvents % 1000 == 0) {
                System.out.printf("已处理 %d 个事件, 当前时间: %d, 剩余事件: %d%n",
                        processedEvents, currentTime, eventQueue.size());
            }
        }

        long endRealTime = System.currentTimeMillis();
        System.out.println("仿真结束!");
        System.out.printf("总事件数: %d%n", processedEvents);
        System.out.printf("仿真时间: %d ms%n", currentTime);
        System.out.printf("实际耗时: %d ms%n", (endRealTime - startRealTime));
    }

    public void stop() { isRunning = false; }
    public void pause() { stop(); }

    private void processEvent(SimEvent event) {
        EventType type = event.getType();
        String entityId = event.getEntityId();
        String instructionId = event.getInstructionId();

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
        System.out.println("\n=== 仿真报告 ===");
        System.out.printf("仿真结束时间: %d ms%n", currentTime);
        System.out.printf("设备总数: %d%n", entityMap.size());
        System.out.printf("指令总数: %d%n", instructionMap.size());

        int completed = 0, inProgress = 0, pending = 0;
        for (Instruction i : instructionMap.values()) {
            if ("COMPLETED".equals(i.getStatus())) completed++;
            else if ("IN_PROGRESS".equals(i.getStatus())) inProgress++;
            else pending++;
        }
        System.out.printf("指令状态: 完成=%d, 进行中=%d, 待处理=%d%n", completed, inProgress, pending);
    }

    public long getCurrentTime() { return currentTime; }
    public Map<String, Entity> getEntities() { return Collections.unmodifiableMap(entityMap); }

    public static class SimulationConfig {
        private long simulationDuration = 86400000;
        private long timeStep = 1000;
        private int maxEvents = 1000000;
        private boolean enableLogging = true;

        public long getSimulationDuration() { return simulationDuration; }
        public void setSimulationDuration(long simulationDuration) { this.simulationDuration = simulationDuration; }
        public long getTimeStep() { return timeStep; }
        public void setTimeStep(long timeStep) { this.timeStep = timeStep; }
        public int getMaxEvents() { return maxEvents; }
        public void setMaxEvents(int maxEvents) { this.maxEvents = maxEvents; }
        public void setEnableLogging(boolean enableLogging) { this.enableLogging = enableLogging; }
    }
}