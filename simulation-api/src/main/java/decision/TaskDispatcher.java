package decision;

import entity.Entity;
import Instruction.Instruction;

/**
 * 任务调度/指派接口
 * 职责：
 * 1. 决定任务的存储与排序（任务安排）
 * 2. 决定将哪个任务分配给哪个实体（任务指派）
 */
public interface TaskDispatcher {

    /**
     * 接收并安排新任务
     * 算法层可以在此实现优先级排序、分类入队等逻辑
     * @param instruction 新提交的任务
     */
    void onNewTaskSubmitted(Instruction instruction);

    /**
     * 为实体指派任务
     * 当实体空闲时调用此方法，算法层需决定返回哪个任务
     * @param entity 请求任务的实体
     * @return 指派的任务，如果没有合适任务则返回 null
     */
    Instruction assignTask(Entity entity);

    /**
     * 任务完成通知
     * 用于算法层清理状态或更新调度表
     * @param instructionId 完成的任务ID
     */
    void onTaskCompleted(String instructionId);
}