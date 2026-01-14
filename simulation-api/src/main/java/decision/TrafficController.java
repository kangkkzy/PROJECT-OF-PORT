package decision;
import entity.Entity;
import Instruction.Instruction;

public interface TrafficController {
    // 决策：发生冲突（物理引擎检测到重叠）时，如何解决？(返回等待指令或重新规划指令)
    Instruction resolveCollision(Entity entity, String obstacleId);

    // 决策：每一步移动前，是否需要强制中断（如紧急停车、红绿灯）
    Instruction checkInterruption(Entity entity);
}