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

    /**
     * 估算移动时间 (修正版)
     * 不再进行路径查找，必须由外部提供路径信息
     * @param entity 实体
     * @param pathIds 路径的路段ID列表
     * @return 估算的毫秒数
     */
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

    // 保留物理公式计算
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
        // 保持原有的作业时间估算逻辑
        switch (entity.getType()) {
            case QC: return 45000; // 简化示例
            case YC: return 50000;
            case IT: return 15000;
            default: return 5000;
        }
    }
}