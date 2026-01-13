package time;

import entity.Entity;
import map.PortMap;
import map.Segment;
import java.util.List;

public class TimeEstimationModule {
    private PortMap portMap;

    public TimeEstimationModule(PortMap portMap) {
        this.portMap = portMap;
    }
// 根据物理情况 计算路径所消耗的时间
    public long estimateMovementTime(Entity entity, List<String> pathIds) {
        if (pathIds == null || pathIds.isEmpty()) {
            return 0;
        }

        double totalDistance = 0.0;
        for (String segmentId : pathIds) {
            Segment seg = portMap.getSegment(segmentId);
            if (seg != null) {
                totalDistance += seg.getLength();
            }
        }

        return calculateMovementTimeWithAcceleration(entity, totalDistance);
    }

    // 物理公式计算
    private long calculateMovementTimeWithAcceleration(Entity entity, double distance) {
        double maxSpeed = entity.getMaxSpeed();
        double acceleration = entity.getAcceleration();
        double deceleration = entity.getDeceleration();

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

    public long estimateExecutionTime(Entity entity, String operationType) {
        // 作业时间估算
        switch (entity.getType()) {
            case QC: return 45000;
            case YC: return 50000;
            case IT: return 15000;
            default: return 5000;
        }
    }
}