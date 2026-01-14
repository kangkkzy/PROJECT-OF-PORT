package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.List;

public interface ExternalTaskService {
    void reportTaskCompletion(String instructionId, String entityId);

    Instruction askForCollisionSolution(String entityId, String segmentId);

    Instruction askForNewTask(Entity entity);

    // 询问是否中断当前任务并执行新任务
    Instruction askForInterruption(Entity entity);

    void submitTask(Instruction instruction);

    List<String> getRoute(String originId, String destinationId);
}