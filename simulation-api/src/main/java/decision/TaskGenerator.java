package decision;

import Instruction.Instruction;

public interface TaskGenerator {
    /**
     * 根据当前仿真时间生成一个新任务
     * @param currentTime 当前仿真毫秒数
     * @return 新生成的指令，如果没有则返回 null
     */
    Instruction generate(long currentTime);
}