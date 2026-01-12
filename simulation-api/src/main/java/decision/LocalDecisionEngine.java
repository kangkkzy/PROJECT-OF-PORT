package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.List;

/**
 * 本地决策引擎 (完全代理版)
 * 职责：
 * 仅仅作为仿真引擎(SimulationEngine)与具体算法(Algo Module)之间的桥梁。
 * 不再包含任何业务逻辑。
 */
public class LocalDecisionEngine implements ExternalTaskService {

    // 注入的两个大脑
    private final RoutePlanner routePlanner;
    private final TaskDispatcher taskDispatcher;

    /**
     * 构造函数注入所有决策模块
     */
    public LocalDecisionEngine(RoutePlanner routePlanner, TaskDispatcher taskDispatcher) {
        this.routePlanner = routePlanner;
        this.taskDispatcher = taskDispatcher;
    }

    // === 路由相关 (转发给 RoutePlanner) ===

    @Override
    public List<String> getRoute(String origin, String destination) {
        return routePlanner.searchRoute(origin, destination);
    }

    // === 任务相关 (转发给 TaskDispatcher) ===

    @Override
    public void submitTask(Instruction instruction) {
        taskDispatcher.onNewTaskSubmitted(instruction);
    }

    @Override
    public Instruction askForNewTask(Entity entity) {
        return taskDispatcher.assignTask(entity);
    }

    @Override
    public void reportTaskCompletion(String instructionId, String entityId) {
        taskDispatcher.onTaskCompleted(instructionId);
    }

    @Override
    public void reportCollision(String entityId, String segmentId) {
        // 碰撞处理也可以抽象成接口，这里暂时保留简单的日志
        System.out.println("决策层收到冲突报告: 实体 " + entityId + " 在路段 " + segmentId);
    }
}