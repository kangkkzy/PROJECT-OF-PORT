package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.List;

public interface ExternalTaskService {
    void reportTaskCompletion(String instructionId, String entityId);

    // 改为请求解决方案
    Instruction askForCollisionSolution(String entityId, String segmentId);

    Instruction askForNewTask(Entity entity);

    void submitTask(Instruction instruction);

    List<String> getRoute(String originId, String destinationId);
}