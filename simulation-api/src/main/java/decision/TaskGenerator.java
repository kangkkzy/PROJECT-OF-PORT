package decision;
import Instruction.Instruction;

public interface TaskGenerator {
    Instruction generate(long currentTime);
}