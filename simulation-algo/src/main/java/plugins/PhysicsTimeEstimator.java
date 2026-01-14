package plugins;

import entity.Entity;
import map.GridMap; // 修改 1: 引入 GridMap
import time.TimeEstimationModule;
import java.util.List;
// 通过物理距离来算时间
public class PhysicsTimeEstimator implements TimeEstimationModule {
    private GridMap gridMap;

    public PhysicsTimeEstimator(GridMap gridMap) {
        this.gridMap = gridMap;
    }

    @Override
    public long estimateMovementTime(Entity entity, List<String> pathIds) {
        if (pathIds == null || pathIds.isEmpty()) {
            return 0;
        }
// 计算距离
        double totalDistance = pathIds.size() * gridMap.getCellSize();

        return calculateMovementTimeWithAcceleration(entity, totalDistance);
    }
    private long calculateMovementTimeWithAcceleration(Entity entity, double distance) {
        double maxSpeed = entity.getMaxSpeed();
        double acceleration = entity.getAcceleration();
        double deceleration = entity.getDeceleration();

        // 避免除零错误
        if (acceleration <= 0 || deceleration <= 0 || maxSpeed <= 0) {
            return (long) (distance / (maxSpeed > 0 ? maxSpeed : 1.0) * 1000);
        }

        double accelerationDistance = (maxSpeed * maxSpeed) / (2 * acceleration);
        double decelerationDistance = (maxSpeed * maxSpeed) / (2 * deceleration);
        double totalAccDecDistance = accelerationDistance + decelerationDistance;

        double totalTime;

        if (distance <= totalAccDecDistance) {
            // 距离太短 无法达到最大速度
            double achievableSpeed = Math.sqrt(
                    (2 * acceleration * deceleration * distance) /
                            (acceleration + deceleration)
            );
            double accTime = achievableSpeed / acceleration;
            double decTime = achievableSpeed / deceleration;
            totalTime = accTime + decTime;
        } else {
            // 梯形速度曲线（加速 -> 匀速 -> 减速）
            double accTime = maxSpeed / acceleration;
            double decTime = maxSpeed / deceleration;
            double cruiseDistance = distance - totalAccDecDistance;
            double cruiseTime = cruiseDistance / maxSpeed;
            totalTime = accTime + cruiseTime + decTime;
        }

        return (long)(totalTime * 1000);
    }
}