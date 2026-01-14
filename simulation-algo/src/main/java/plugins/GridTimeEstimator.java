package plugins;

import entity.Entity;
import map.GridMap;
import map.Location; // 必须导入这个新类
import time.TimeEstimationModule;

import java.util.List;

public class GridTimeEstimator implements TimeEstimationModule {
    private final GridMap gridMap;

    public GridTimeEstimator(GridMap gridMap) {
        this.gridMap = gridMap;
    }

    @Override
    public long estimateMovementTime(Entity entity, List<Location> path) {
        // 参数类型已从 List<String> 改为 List<Location>
        if (path == null || path.isEmpty()) return 0;

        // 逻辑保持不变：距离 = 格子数 * 格子大小
        double distance = path.size() * gridMap.getCellSize();
        double speed = entity.getMaxSpeed();

        if (speed <= 0) return 1000;

        return (long) ((distance / speed) * 1000);
    }
}