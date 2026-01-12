import entity.*;
import event.*;
import Instruction.*;
import map.PortMap;
import decision.DecisionModule;
import time.TimeEstimationModule;
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
        this.scheduler = new SimpleScheduler(decisionModule, timeModule);
    }

    public void addEntity(Entity entity) {
        entityMap.put(entity.getId(), entity);
        scheduler.registerEntity(entity);
    }

    public void addInstruction(Instruction instruction) {
        instructionMap.put(instruction.getInstructionId(), instruction);
        scheduler.addInstruction(instruction);

        // 如果指令有生成时间，添加初始事件
        // 这里简化处理，立即生成事件
        if (instruction.getStatus().equals("PENDING")) {
            // 可以根据需要添加初始事件
        }
    }

    public void addEvent(SimEvent event) {
        eventQueue.add(event);
    }

    public void start() {
        if (isRunning) {
            throw new IllegalStateException("仿真已经在运行中");
        }

        isRunning = true;
        System.out.println("仿真开始...");

        long startRealTime = System.currentTimeMillis();
        long maxSimulationTime = config.getSimulationDuration();
        int processedEvents = 0;

        while (isRunning && currentTime < maxSimulationTime && !eventQueue.isEmpty()) {
            SimEvent event = eventQueue.poll();
            if (event == null) {
                break;
            }

            // 推进仿真时间
            currentTime = event.getTimestamp();

            // 处理事件
            processEvent(event);

            // 记录事件
            eventLog.add(event);

            processedEvents++;

            // 检查事件数量限制
            if (processedEvents >= config.getMaxEvents()) {
                System.out.println("达到最大事件数量限制: " + config.getMaxEvents());
                break;
            }

            // 输出进度
            if (processedEvents % 1000 == 0) {
                System.out.printf("已处理 %d 个事件, 当前时间: %d, 剩余事件: %d%n",
                        processedEvents, currentTime, eventQueue.size());
            }
        }

        long endRealTime = System.currentTimeMillis();
        long realTimeElapsed = endRealTime - startRealTime;

        System.out.println("仿真结束!");
        System.out.printf("总事件数: %d%n", processedEvents);
        System.out.printf("仿真时间: %d ms%n", currentTime);
        System.out.printf("实际耗时: %d ms%n", realTimeElapsed);
        System.out.printf("事件队列剩余: %d%n", eventQueue.size());

        isRunning = false;
    }

    public void stop() {
        isRunning = false;
        System.out.println("仿真被手动停止");
    }

    public void pause() {
        // 简化实现，直接停止
        stop();
    }

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
                // 类似桥吊处理
                // scheduler.handleYCExecutionComplete(currentTime, entityId, instructionId);
                break;

            case YC_ARRIVAL:
                // scheduler.handleYCArrival(currentTime, entityId, instructionId, event.getTargetPosition());
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
        System.out.printf("处理事件数: %d%n", eventLog.size());

        // 统计设备状态
        Map<EntityStatus, Integer> statusCount = new HashMap<>();
        for (Entity entity : entityMap.values()) {
            statusCount.merge(entity.getStatus(), 1, Integer::sum);
        }

        System.out.println("\n设备状态统计:");
        for (EntityStatus status : EntityStatus.values()) {
            int count = statusCount.getOrDefault(status, 0);
            System.out.printf("  %s: %d%n", status.getChineseName(), count);
        }

        // 统计指令完成情况
        int completed = 0;
        int inProgress = 0;
        int pending = 0;

        for (Instruction instruction : instructionMap.values()) {
            if ("COMPLETED".equals(instruction.getStatus())) {
                completed++;
            } else if ("IN_PROGRESS".equals(instruction.getStatus())) {
                inProgress++;
            } else {
                pending++;
            }
        }

        System.out.println("\n指令完成情况:");
        System.out.printf("  已完成: %d%n", completed);
        System.out.printf("  进行中: %d%n", inProgress);
        System.out.printf("  待处理: %d%n", pending);

        // 输出事件日志摘要
        System.out.println("\n最后10个事件:");
        int start = Math.max(0, eventLog.size() - 10);
        for (int i = start; i < eventLog.size(); i++) {
            System.out.println("  " + eventLog.get(i));
        }
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public Map<String, Entity> getEntities() {
        return Collections.unmodifiableMap(entityMap);
    }

    public Map<String, Instruction> getInstructions() {
        return Collections.unmodifiableMap(instructionMap);
    }

    public List<SimEvent> getEventLog() {
        return Collections.unmodifiableList(eventLog);
    }

    public boolean isRunning() {
        return isRunning;
    }

    // 配置类
    public static class SimulationConfig {
        private long simulationDuration = 86400000; // 24小时
        private long timeStep = 1000; // 1秒
        private int maxEvents = 1000000;
        private boolean enableLogging = true;

        public long getSimulationDuration() { return simulationDuration; }
        public void setSimulationDuration(long simulationDuration) { this.simulationDuration = simulationDuration; }

        public long getTimeStep() { return timeStep; }
        public void setTimeStep(long timeStep) { this.timeStep = timeStep; }

        public int getMaxEvents() { return maxEvents; }
        public void setMaxEvents(int maxEvents) { this.maxEvents = maxEvents; }

        public boolean isEnableLogging() { return enableLogging; }
        public void setEnableLogging(boolean enableLogging) { this.enableLogging = enableLogging; }
    }
}