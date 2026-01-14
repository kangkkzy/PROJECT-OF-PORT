package plugins;

import entity.Entity;
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
        // 简单估算：距离 / 速度
        double distance = path.size() * gridMap.getCellSize();
        double speed = Math.max(entity.getMaxSpeed(), 0.1); // 防止除零
        return (long) ((distance / speed) * 1000);
    }
}