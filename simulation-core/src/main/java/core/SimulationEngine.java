package core;

import algo.SimpleScheduler;
import event.EventType;
import event.SimEvent;
import java.util.*;

public class SimulationEngine {
    private final long endTime;
    private final int maxEvents;
    private final SimpleScheduler scheduler;

    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();
    private final List<SimEvent> eventLog = new ArrayList<>();
    private long currentTime = 0;
    private boolean isRunning = false;

    public SimulationEngine(long endTime, int maxEvents, SimpleScheduler scheduler) {
        this.endTime = endTime;
        this.maxEvents = maxEvents;
        this.scheduler = scheduler;
    }

    public List<SimEvent> getEventLog() { return eventLog; }
    public void addEntity(entity.Entity e) { scheduler.registerEntity(e); }
    public void addInstruction(Instruction.Instruction i) { scheduler.addInstruction(i); }

    public void start() {
        isRunning = true;
        System.out.println(">>> 仿真引擎启动 | 时长限制: " + endTime + "ms");

        while (isRunning && currentTime < endTime &&
                (!eventQueue.isEmpty() || scheduler.hasPendingEvents())) {

            SimEvent event = scheduler.getNextEvent();
            if (event == null) event = eventQueue.poll();
            if (event == null) break;

            currentTime = Math.max(currentTime, event.getTimestamp());

            // 仅记录非步进的关键业务日志
            if (event.getType() != EventType.MOVE_STEP) {
                eventLog.add(event);
                System.out.printf("[%d] 处理事件: %s (实体: %s)\n",
                        currentTime, event.getType().getChineseName(), event.getEntityId());
            }

            dispatch(event);

            if (eventLog.size() >= maxEvents) {
                System.out.println(">>> 达到最大事件数限制");
                break;
            }
        }
        System.out.println(">>> 仿真结束");
    }

    private void dispatch(SimEvent event) {
        long now = currentTime;
        String eid = event.getEntityId();
        String iid = event.getInstructionId();
        String pos = event.getTargetPosition();

        switch (event.getType()) {
            case TASK_GENERATION -> scheduler.handleTaskGeneration(now);
            case MOVE_STEP       -> scheduler.handleStepArrival(now, eid, iid, pos);

            case QC_EXECUTION_COMPLETE -> scheduler.handleQCExecutionComplete(now, eid, iid);
            case QC_ARRIVAL            -> scheduler.handleQCArrival(now, eid, iid, pos);

            case YC_EXECUTION_COMPLETE -> scheduler.handleYCExecutionComplete(now, eid, iid);
            case YC_ARRIVAL            -> scheduler.handleYCArrival(now, eid, iid, pos);

            case IT_EXECUTION_COMPLETE -> scheduler.handleITExecutionComplete(now, eid, iid);
            case IT_ARRIVAL            -> scheduler.handleITArrival(now, eid, iid, pos);

            default -> System.err.println("丢弃未知事件: " + event.getType());
        }
    }
}