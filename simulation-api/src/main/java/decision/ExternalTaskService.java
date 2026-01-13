package decision;

import entity.Entity;
import Instruction.Instruction;
import java.util.List;
// 外部任务服务接口
public interface ExternalTaskService {
// 报告任务完成
    void reportTaskCompletion(String instructionId, String entityId);
// 报告存在物理冲突 会发生不可避免的碰撞
    void reportCollision(String entityId, String segmentId);
// 请求给新的任务
    Instruction askForNewTask(Entity entity);
// 提交任务
    void submitTask(Instruction instruction);
// 获取移动路径 起点 终点 路段列表
    List<String> getRoute(String originId, String destinationId);
}