package time;

import entity.Entity;
import Instruction.Instruction;
import map.Location;
import java.util.List;

public interface TimeEstimationModule {
    // 移动耗时
    long estimateMovementTime(Entity entity, List<Location> path);

    // 作业耗时
    long estimateOperationTime(Entity entity, Instruction instruction);
}