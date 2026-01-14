package algo;

import entity.Entity;
import entity.EntityStatus;
import Instruction.Instruction;
import Instruction.InstructionType;
import decision.*;
import map.GridMap;
import map.Location;
import physics.PhysicsEngine;
import event.EventType;
import event.SimEvent;
import time.TimeEstimationModule;
import java.util.*;

public class SimpleScheduler {
    // 仅持有必要的策略接口
    private final TaskAllocator taskAllocator;
    private final TrafficController trafficController;
    private final RoutePlanner routePlanner;
    private final TaskGenerator taskGenerator;

    // 基础设施
    private final TimeEstimationModule timeModule;
    private final PhysicsEngine physicsEngine;
    private final GridMap gridMap;

    private final Map<String, Entity> entities = new HashMap<>();
    private final Map<String, Instruction> instructions = new HashMap<>();
    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();

    public SimpleScheduler(TaskAllocator taskAllocator,
                           TrafficController trafficController,
                           RoutePlanner routePlanner,
                           TimeEstimationModule timeModule,
                           PhysicsEngine physicsEngine,
                           GridMap gridMap,
                           TaskGenerator taskGenerator) {
        this.taskAllocator = taskAllocator;
        this.trafficController = trafficController;
        this.routePlanner = routePlanner;
        this.timeModule = timeModule;
        this.physicsEngine = physicsEngine;
        this.gridMap = gridMap;
        this.taskGenerator = taskGenerator;
    }

    // 1. 初始化逻辑：满足“根据状态添加事件”
    public void init() {
        for (Entity entity : entities.values()) {
            // 询问插件：该实体该干什么？
            Instruction inst = taskAllocator.assignTask(entity);
            if (inst != null) {
                // 如果插件给了任务，就开始执行
                startInstruction(0, entity, inst);
            }
        }
        // 启动任务生成器
        if (taskGenerator != null) {
            eventQueue.add(new SimEvent(0, EventType.TASK_GENERATION, "SYSTEM"));
        }
    }

    // 2. 运行时逻辑：处理移动
    public void handleStepArrival(long currentTime, String entityId, String instructionId, String targetPosKey) {
        Entity entity = entities.get(entityId);
        if (entity == null) return;

        // 更新物理位置
        Location targetLoc = targetPosKey != null ? Location.parse(targetPosKey) : entity.getCurrentLocation();
        Location oldLoc = entity.getCurrentLocation();

        if (oldLoc != null && !oldLoc.equals(targetLoc)) {
            physicsEngine.unlockSingleResource(entityId, oldLoc);
        }
        entity.setCurrentLocation(targetLoc);
        entity.popNextStep();

        // 询问下一步
        processNextStep(currentTime, entity);
    }

    private void processNextStep(long currentTime, Entity entity) {
        // 询问插件：是否有交通管制（中断）？
        Instruction interrupt = trafficController.checkInterruption(entity);
        if (interrupt != null) {
            startInstruction(currentTime, entity, interrupt);
            return;
        }

        // 如果路径走完了
        if (!entity.hasRemainingPath()) {
            handleInstructionComplete(currentTime, entity);
            return;
        }

        Location nextLoc = entity.getRemainingPath().get(0);

        // 询问插件：遇到物理碰撞怎么办？
        if (physicsEngine.detectCollision(nextLoc, entity.getId())) {
            String obstacle = physicsEngine.getOccupier(nextLoc);
            // 这里还可以加入判断是否是目标对象的逻辑，这里从简
            Instruction recovery = trafficController.resolveCollision(entity, obstacle);
            if (recovery != null) {
                startInstruction(currentTime, entity, recovery);
                return;
            }
        }

        // 执行移动：这是 Scheduler 的本职工作（计算时间，添加事件）
        physicsEngine.lockResources(entity.getId(), Collections.singletonList(nextLoc));
        long duration = timeModule.estimateMovementTime(entity, Collections.singletonList(nextLoc));

        // 产生新事件
        eventQueue.add(new SimEvent(
                currentTime + duration,
                EventType.MOVE_STEP,
                entity.getId(),
                entity.getCurrentInstructionId(),
                nextLoc.toKey()
        ));
    }

    private void startInstruction(long currentTime, Entity entity, Instruction inst) {
        instructions.put(inst.getInstructionId(), inst);
        entity.setCurrentInstructionId(inst.getInstructionId());
        inst.markInProgress();

        if (inst.getType() == InstructionType.MOVE || inst.getType() == InstructionType.LOAD_TO_SHIP) {
            // 询问插件：怎么走？
            List<Location> path = routePlanner.searchRoute(
                    Location.parse(inst.getOrigin()),
                    Location.parse(inst.getDestination()) // 注意：Instruction内最好也存Location对象，这里假设做了转换
            );
            entity.setRemainingPath(path);
            processNextStep(currentTime, entity);
        } else if (inst.getType() == InstructionType.WAIT) {
            // 执行等待
            long duration = inst.getExpectedDuration();
            eventQueue.add(new SimEvent(currentTime + duration, EventType.IT_EXECUTION_COMPLETE, entity.getId(), inst.getInstructionId()));
        }
    }

    private void handleInstructionComplete(long currentTime, Entity entity) {
        Instruction inst = instructions.get(entity.getCurrentInstructionId());
        if (inst != null) {
            inst.markCompleted();
            // 通知插件：任务完事了
            taskAllocator.onTaskCompleted(inst.getInstructionId());
        }

        entity.setStatus(EntityStatus.IDLE);
        entity.setCurrentInstructionId(null);

        // 询问插件：还有新任务吗？
        Instruction next = taskAllocator.assignTask(entity);
        if (next != null) {
            startInstruction(currentTime, entity, next);
        }
    }

    // ... 其他 getter/setter 和注册方法保持不变
    public void registerEntity(Entity entity) { entities.put(entity.getId(), entity); }
    public void addInstruction(Instruction task) {
        instructions.put(task.getInstructionId(), task);
        taskAllocator.onNewTaskSubmitted(task); // 通知插件
    }
    public SimEvent getNextEvent() { return eventQueue.poll(); }
    public boolean hasPendingEvents() { return !eventQueue.isEmpty(); }
    // 需要加上处理 TASK_GENERATION 的逻辑，调用 taskGenerator.generate()
    public void handleTaskGeneration(long now) {
        Instruction task = taskGenerator.generate(now);
        if(task != null) addInstruction(task);
        eventQueue.add(new SimEvent(now + 5000, EventType.TASK_GENERATION, "SYSTEM"));
    }
}