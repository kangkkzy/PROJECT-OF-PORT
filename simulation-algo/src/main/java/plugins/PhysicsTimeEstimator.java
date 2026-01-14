package plugins;

import entity.Entity;
import map.GridMap;
import map.Location;
import time.TimeEstimationModule;
import java.util.List;

public class PhysicsTimeEstimator implements TimeEstimationModule {
    private final GridMap gridMap;

    public PhysicsTimeEstimator(GridMap gridMap) {
        this.gridMap = gridMap;
    }

    @Override
    public long estimateMovementTime(Entity entity, List<Location> path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        // 计算距离：路径点数量 * 格子大小
        double distance = path.size() * gridMap.getCellSize();

        return calculateMovementTimeWithAcceleration(entity, distance);
    }

    private long calculateMovementTimeWithAcceleration(Entity entity, double distance) {
        double maxSpeed = entity.getMaxSpeed();
        double acceleration = entity.getAcceleration();
        double deceleration = entity.getDeceleration();

        if (acceleration <= 0 || deceleration <= 0 || maxSpeed <= 0) {
            return (long) (distance / (maxSpeed > 0 ? maxSpeed : 1.0) * 1000);
        }

        double accelerationDistance = (maxSpeed * maxSpeed) / (2 * acceleration);
        double decelerationDistance = (maxSpeed * maxSpeed) / (2 * deceleration);
        double totalAccDecDistance = accelerationDistance + decelerationDistance;

        double totalTime;

        if (distance <= totalAccDecDistance) {
            double achievableSpeed = Math.sqrt(
                    (2 * acceleration * deceleration * distance) /
                            (acceleration + deceleration)
            );
            double accTime = achievableSpeed / acceleration;
            double decTime = achievableSpeed / deceleration;
            totalTime = accTime + decTime;
        } else {
            double accTime = maxSpeed / acceleration;
            double decTime = maxSpeed / deceleration;
            double cruiseDistance = distance - totalAccDecDistance;
            double cruiseTime = cruiseDistance / maxSpeed;
            totalTime = accTime + cruiseTime + decTime;
        }

        return (long)(totalTime * 1000);
    }
}