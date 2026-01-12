package core;

import entity.*;
import event.*;
import Instruction.*;
import map.PortMap;
import decision.ExternalTaskService; // 引入接口
import time.TimeEstimationModule;
import algo.SimpleScheduler;
import java.util.*;

public class SimulationEngine {
    private long currentTime;
    private PriorityQueue<SimEvent> eventQueue;
    private Map<String, Instruction> instructionMap;
    private Map<String, Entity> entityMap;
    private PortMap portMap;

    // 修改：不再持有具体的 DecisionModule
    private ExternalTaskService taskService;
    private TimeEstimationModule timeModule;
    private SimpleScheduler scheduler;
    private List<SimEvent> eventLog;
    private SimulationConfig config;
    private boolean isRunning;

    // 修改构造函数，传入 ExternalTaskService
    public SimulationEngine(PortMap portMap, SimulationConfig config, ExternalTaskService taskService) {
        this.currentTime = 0;
        this.eventQueue = new PriorityQueue<>();
        this.instructionMap = new HashMap<>();
        this.entityMap = new HashMap<>();
        this.portMap = portMap;
        this.config = config;
        this.eventLog = new ArrayList<>();
        this.isRunning = false;

        // 注入外部传入的任务服务
        this.taskService = taskService;

        // 依然保留 TimeEstimationModule (物理计算)
        this.timeModule = new TimeEstimationModule(portMap);

        // 传递给 Scheduler
        this.scheduler = new SimpleScheduler(taskService, timeModule);
    }

    public void addEntity(Entity entity) {
        entityMap.put(entity.getId(), entity);
        scheduler.registerEntity(entity);
    }

    public void addInstruction(Instruction instruction) {
        instructionMap.put(instruction.getInstructionId(), instruction);
        scheduler.addInstruction(instruction);
    }

    // ... start(), stop(), addEvent() 等其他方法保持不变 ...

    // 确保 processEvent 方法也保持不变，因为它只负责分发给 scheduler
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
            // ... 其他 case 保持不变 ...
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

    // ... generateReport 和 config 类保持不变 ...
    public void generateReport() {
        // ... (略) ...
    }

    public void start() {
        // ... (略) ...
        if (isRunning) throw new IllegalStateException("仿真已经在运行中");
        isRunning = true;
        System.out.println("仿真开始...");

        long maxSimulationTime = config.getSimulationDuration();
        int processedEvents = 0;

        while (isRunning && currentTime < maxSimulationTime && !eventQueue.isEmpty()) {
            SimEvent event = eventQueue.poll();
            if (event == null) break;
            currentTime = event.getTimestamp();
            processEvent(event);
            eventLog.add(event);
            processedEvents++;
            // ... (略) ...
        }
        System.out.println("仿真结束!");
    }

    public static class SimulationConfig {
        // ... (略) ...
        private long simulationDuration = 86400000;
        private long timeStep = 1000;
        private int maxEvents = 1000000;
        public long getSimulationDuration() { return simulationDuration; }
        public void setSimulationDuration(long simulationDuration) { this.simulationDuration = simulationDuration; }
        public void setMaxEvents(int maxEvents) { this.maxEvents = maxEvents; }
        public int getMaxEvents() { return maxEvents; }
        public void setTimeStep(long timeStep) { this.timeStep = timeStep; }
    }
}