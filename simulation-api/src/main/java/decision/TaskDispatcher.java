// 文件: simulation-api/src/main/java/decision/TaskDispatcher.java
package decision;

import entity.Entity;
import Instruction.Instruction;

public interface TaskDispatcher {
    void onNewTaskSubmitted(Instruction instruction);
    Instruction assignTask(Entity entity);
    void onTaskCompleted(String instructionId);
    Instruction resolveCollision(String entityId, String segmentId);

    // 检查是否需要中断当前行为
    Instruction checkInterruption(Entity entity);

    // 【新增】设置紧急暂停状态的接口方法（可选，方便外部调用）
    void setEmergencyStop(boolean stop);
}