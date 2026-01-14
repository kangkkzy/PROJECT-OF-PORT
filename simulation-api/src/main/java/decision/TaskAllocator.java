package decision;
import entity.Entity;
import Instruction.Instruction;

// 职责：负责任务的存储、排序和分发
public interface TaskAllocator {
    void onNewTaskSubmitted(Instruction instruction);
    Instruction assignTask(Entity entity);
    void onTaskCompleted(String instructionId);
}
