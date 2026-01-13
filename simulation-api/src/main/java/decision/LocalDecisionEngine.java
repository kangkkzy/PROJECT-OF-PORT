package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.List;

// 连接外部接口和内部
public class LocalDecisionEngine implements ExternalTaskService {

    private final RoutePlanner routePlanner;
    private final TaskDispatcher taskDispatcher;
// 连接决策模块

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

    @Override
    public void reportCollision(String entityId, String segmentId) {
        // 处理碰撞的接口
        System.out.println("决策层收到冲突报告: 实体 " + entityId + " 在路段 " + segmentId);
    }
}