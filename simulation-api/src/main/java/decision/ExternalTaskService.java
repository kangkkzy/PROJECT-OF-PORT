package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.List;

/**
 * 外部任务服务接口
 * 定义了仿真系统(Client)与任务决策系统(Server)的交互标准
 * [修正] 增加了路径规划和冲突解决接口
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

    /**
     * [新增] 外部路径规划
     * @param origin 起点节点ID
     * @param destination 终点节点ID
     * @return 路径节点ID列表
     */
    List<String> findPath(String origin, String destination);

    /**
     * [新增] 解决物理冲突
     * 当仿真引擎检测到物理冲突（如路径被占用）时调用
     * @param entity 发生冲突的实体
     * @param currentInstruction 当前正在尝试执行的指令
     * @return 解决冲突的新指令（如 WAIT 指令或重新规划路径后的 MOVE 指令）
     */
    Instruction resolveCollision(Entity entity, Instruction currentInstruction);
}