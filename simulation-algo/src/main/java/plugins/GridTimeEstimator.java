package plugins;

import entity.Entity;
import map.GridMap;
import time.TimeEstimationModule;
import java.util.List;

public class GridTimeEstimator implements TimeEstimationModule {
    private final GridMap gridMap;

    public GridTimeEstimator(GridMap gridMap) {
        this.gridMap = gridMap;
    }

    @Override
    public long estimateMovementTime(Entity entity, List<String> pathIds) {
        // pathIds 是 grid keys ("x_y") 的列表
        if (pathIds == null || pathIds.isEmpty()) return 0;

        // 距离 = 格子数 * 格子大小
        double distance = pathIds.size() * gridMap.getCellSize();
        double speed = entity.getMaxSpeed();

        if (speed <= 0) return 1000; // 防止除零

        // 简单匀速运动公式：时间 = 距离 / 速度 * 1000 (ms)
        return (long) ((distance / speed) * 1000);
    }
}