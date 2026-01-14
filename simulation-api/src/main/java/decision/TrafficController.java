package decision;
import entity.Entity;
import Instruction.Instruction;

// 职责：负责交通管制和避障
public interface TrafficController {
    // 解决冲突：返回等待或重路由指令
    Instruction resolveCollision(Entity entity, String obstacleId);

    // 检查中断：例如紧急停车
    Instruction checkInterruption(Entity entity);

    // 设置紧急停止状态
    void setEmergencyStop(boolean stop);
}
