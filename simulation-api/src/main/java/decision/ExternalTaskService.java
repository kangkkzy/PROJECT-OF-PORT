package decision;

import entity.Entity;
import Instruction.Instruction;

/**
 * 外部任务服务接口
 * 定义了仿真系统(Client)与任务决策系统(Server)的交互标准
 */
public interface ExternalTaskService {

    /**
     * 上报任务完成
     * 当设备完成当前指令时调用
     */
    void reportTaskCompletion(String instructionId, String entityId);

    /**
     * 请求新任务
     * 当设备空闲时调用，索取下一个指令
     * @return 下一条指令，如果没有任务则返回 null
     */
    Instruction askForNewTask(Entity entity);

    /**
     * 提交初始任务 (用于本地测试或初始化)
     */
    void submitTask(Instruction instruction);
}