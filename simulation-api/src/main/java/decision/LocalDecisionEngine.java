package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.List;

// 负责将核心引擎的请求转发给具体的算法实现
public class LocalDecisionEngine implements ExternalTaskService {

    private final RoutePlanner routePlanner;
    private final TaskDispatcher taskDispatcher;

    public LocalDecisionEngine(RoutePlanner routePlanner, TaskDispatcher taskDispatcher) {
        this.routePlanner = routePlanner;
        this.taskDispatcher = taskDispatcher;
    }

    // 连接路径规划模块
    @Override
    public List<String> getRoute(String origin, String destination) {
        return routePlanner.searchRoute(origin, destination);
    }

    // 连接任务模块
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

    // 实现接口要求的方法，将冲突解决委托给 TaskDispatcher
    @Override
    public Instruction askForCollisionSolution(String entityId, String segmentId) {
        System.out.println("决策层: 实体 " + entityId + " 在 " + segmentId + " 遭遇冲突，请求解决方案...");
        return taskDispatcher.resolveCollision(entityId, segmentId);
    }
}