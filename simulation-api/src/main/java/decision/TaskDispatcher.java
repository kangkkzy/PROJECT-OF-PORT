package decision;

import entity.Entity;
import Instruction.Instruction;

public interface TaskDispatcher {
    void onNewTaskSubmitted(Instruction instruction);
    Instruction assignTask(Entity entity);
    void onTaskCompleted(String instructionId);

    // 冲突解决也完全委托给外部算法
    Instruction resolveCollision(String entityId, String segmentId);
}