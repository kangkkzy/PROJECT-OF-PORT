package time;

import entity.Entity;
import map.PortMap;
import map.Segment;
import java.util.List;
// 移动时间计算
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

    // 物理公式计算：考虑加速、匀速、减速过程
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