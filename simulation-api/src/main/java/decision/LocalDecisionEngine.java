package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.List;

public class LocalDecisionEngine implements ExternalTaskService {

    private final RoutePlanner routePlanner;
    private final TaskDispatcher taskDispatcher;

    // 构造注入：通过接口接收算法实例
    public LocalDecisionEngine(RoutePlanner routePlanner, TaskDispatcher taskDispatcher) {
        this.routePlanner = routePlanner;
        this.taskDispatcher = taskDispatcher;
    }

    @Override
    public List<String> getRoute(String origin, String destination) {
        return routePlanner.searchRoute(origin, destination);
    }

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
    public Instruction askForCollisionSolution(String entityId, String segmentId) {
        // 直接转发给外部算法，不在这里做决定
        return taskDispatcher.resolveCollision(entityId, segmentId);
    }
}