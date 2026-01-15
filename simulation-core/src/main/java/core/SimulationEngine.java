package core;

import algo.SimpleScheduler;
import decision.MetricsAnalyzer;
import decision.SimulationValidator;
import event.EventType;
import event.SimEvent;
import java.util.*;

public class SimulationEngine {
    private final long endTime;
    private final int maxEvents;
    private final SimpleScheduler scheduler;
    private final List<SimEvent> eventLog = new ArrayList<>();
    private long currentTime = 0;
    private boolean isRunning = false;

    // 新增分析组件
    private SimulationValidator validator;
    private MetricsAnalyzer analyzer;

    public SimulationEngine(long endTime, int maxEvents, SimpleScheduler scheduler) {
        this.endTime = endTime;
        this.maxEvents = maxEvents;
        this.scheduler = scheduler;
    }

    public void setValidator(SimulationValidator v) { this.validator = v; }
    public void setAnalyzer(MetricsAnalyzer a) { this.analyzer = a; }

    public void start() {
        isRunning = true;
        System.out.println(">>> 仿真引擎启动 | 截止时间: " + endTime + "ms");

        while (isRunning && currentTime < endTime) {
            SimEvent event = scheduler.getNextEvent();
            if (event == null) break;

            currentTime = Math.max(currentTime, event.getTimestamp());
            if (event.getType() != EventType.MOVE_STEP) {
                eventLog.add(event);
            }

            dispatch(event);

            if (eventLog.size() >= maxEvents) break;
        }

        System.out.println(">>> 核心仿真运行结束，开始数据自动化分析...");
        runAnalysis();
    }

    private void runAnalysis() {
        if (validator != null) {
            System.out.println("--- [正确性自检报告] ---");
            validator.validate(eventLog).forEach(msg -> System.out.println("  " + msg));
        }
        if (analyzer != null) {
            System.out.println("--- [算法迭代 KPI 报告] ---");
            Map<String, Object> kpis = analyzer.analyze(eventLog);
            kpis.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        }
    }

    private void dispatch(SimEvent event) {
        long now = currentTime;
        String eid = event.getEntityId();
        String iid = event.getInstructionId();
        switch (event.getType()) {
            case TASK_GENERATION -> scheduler.handleTaskGeneration(now);
            case MOVE_STEP -> scheduler.handleStepArrival(now, eid, event.getTargetPosition());
            case QC_EXECUTION_COMPLETE, YC_EXECUTION_COMPLETE -> scheduler.handleCraneExecutionComplete(now, eid, iid);
            case QC_ARRIVAL, YC_ARRIVAL -> scheduler.handleCraneArrival(now, eid, iid);
            case IT_EXECUTION_COMPLETE -> scheduler.handleITExecutionComplete(now, eid, iid);
            case IT_ARRIVAL -> scheduler.handleITArrival(now, eid, iid);
            default -> {}
        }
    }

    public List<SimEvent> getEventLog() { return eventLog; }
}