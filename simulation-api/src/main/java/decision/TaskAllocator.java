package decision;
import entity.Entity;
import Instruction.Instruction;

public interface TaskAllocator {
    // 决策：当有新指令提交时，如何存储或排序
    void onNewTaskSubmitted(Instruction instruction);

    // 决策：当设备空闲时，分派什么任务给它 (返回 null 代表无任务)
    Instruction assignTask(Entity entity);

    // 状态更新：通知算法任务已完成，以便清理内部队列
    void onTaskCompleted(String instructionId);
}
