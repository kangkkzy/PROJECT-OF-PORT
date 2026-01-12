package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.List;

/**
 * 外部任务服务接口 (修正版)
 * 这是一个纯粹的外部交互接口，所有“大脑”的工作都在这里定义
 */
public interface ExternalTaskService {

    /**
     * 上报任务完成
     */
    void reportTaskCompletion(String instructionId, String entityId);

    /**
     * 上报物理冲突
     * 当物理引擎检测到不可避免的碰撞时调用
     */
    void reportCollision(String entityId, String segmentId);

    /**
     * 请求新任务
     */
    Instruction askForNewTask(Entity entity);

    /**
     * 提交任务
     */
    void submitTask(Instruction instruction);

    /**
     * [关键修改] 获取移动路径
     * 仿真器不自己寻路，而是向外部询问：从A到B应该走哪些路段？
     * @param originId 起点ID
     * @param destinationId 终点ID
     * @return 路段ID列表 (Segment IDs)
     */
    List<String> getRoute(String originId, String destinationId);
}