package decision;

import entity.Entity;
import Instruction.Instruction;
// 任务调度接口
public interface TaskDispatcher {
// 接收并安排新任务
    void onNewTaskSubmitted(Instruction instruction);
// 给具体的实体指派任务
    Instruction assignTask(Entity entity);
// 任务结束
    void onTaskCompleted(String instructionId);
}