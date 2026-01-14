package plugins;

import entity.Entity;
import entity.QC;
import entity.YC;
import Instruction.Instruction;
import map.GridMap;
import map.Location;
import time.TimeEstimationModule;
import java.util.List;

public class GridTimeEstimator implements TimeEstimationModule {
    private final GridMap gridMap;

    public GridTimeEstimator(GridMap gridMap) { this.gridMap = gridMap; }

    @Override
    public long estimateMovementTime(Entity entity, List<Location> path) {
        if (path == null || path.isEmpty()) return 0;
        double distance = path.size() * gridMap.getCellSize();
        double speed = Math.max(entity.getMaxSpeed(), 0.1);
        return (long) ((distance / speed) * 1000);
    }

    @Override
    public long estimateOperationTime(Entity entity, Instruction inst) {
        // 如果指令自带预估耗时，优先使用（模拟 TOS 数据）
        if (inst.getExpectedDuration() > 0) return inst.getExpectedDuration();

        // 否则根据设备性能计算 (这里仅作简单示例，可扩展)
        if (entity instanceof QC qc) {
            return (long) (qc.calculateLiftTime(20.0) * 1000);
        } else if (entity instanceof YC yc) {
            return (long) (yc.calculateCycleTime(3) * 1000);
        }
        return 30000; // 默认 30s
    }
}