package time;

import entity.Entity;
import map.PortMap;
import java.util.List;

public class TimeEstimationModule {
    private PortMap portMap;

    public TimeEstimationModule(PortMap portMap) {
        this.portMap = portMap;
    }

    public long estimateMovementTime(Entity entity, String fromPosition, String toPosition) {
        if (fromPosition.equals(toPosition)) {
            return 0;
        }

        // 查找路径
        List<String> path = portMap.findPath(fromPosition, toPosition);
        if (path.isEmpty()) {
            return Long.MAX_VALUE; // 无法到达
        }

        // 计算路径距离
        double distance = portMap.calculatePathDistance(path);

        // 计算移动时间（考虑加减速）
        return calculateMovementTimeWithAcceleration(entity, distance);
    }

    private long calculateMovementTimeWithAcceleration(Entity entity, double distance) {
        double maxSpeed = entity.getMaxSpeed();
        double acceleration = entity.getAcceleration();
        double deceleration = entity.getDeceleration();

        // 计算加速到最大速度所需距离
        double accelerationDistance = (maxSpeed * maxSpeed) / (2 * acceleration);
        double decelerationDistance = (maxSpeed * maxSpeed) / (2 * deceleration);
        double totalAccDecDistance = accelerationDistance + decelerationDistance;

        double totalTime;

        if (distance <= totalAccDecDistance) {
            // 距离太短，无法加速到最大速度
            double achievableSpeed = Math.sqrt(
                    (2 * acceleration * deceleration * distance) /
                            (acceleration + deceleration)
            );

            double accTime = achievableSpeed / acceleration;
            double decTime = achievableSpeed / deceleration;
            totalTime = accTime + decTime;
        } else {
            // 完整的加速-匀速-减速过程
            double accTime = maxSpeed / acceleration;
            double decTime = maxSpeed / deceleration;
            double cruiseDistance = distance - totalAccDecDistance;
            double cruiseTime = cruiseDistance / maxSpeed;
            totalTime = accTime + cruiseTime + decTime;
        }

        // 转换为毫秒
        return (long)(totalTime * 1000);
    }

    public long estimateExecutionTime(Entity entity, String operationType) {
        // 根据设备类型和操作类型估算执行时间
        switch (entity.getType()) {
            case QC:
                return estimateQCOperationTime(operationType);
            case YC:
                return estimateYCOperationTime(operationType);
            case IT:
                return estimateITOperationTime(operationType);
            default:
                return 30000; // 默认30秒
        }
    }

    private long estimateQCOperationTime(String operationType) {
        // 桥吊操作时间估算
        if ("LOAD".equals(operationType)) {
            return 45000; // 45秒装船
        } else if ("UNLOAD".equals(operationType)) {
            return 40000; // 40秒卸船
        }
        return 35000; // 35秒默认
    }

    private long estimateYCOperationTime(String operationType) {
        // 龙门吊操作时间估算
        if ("STACK".equals(operationType)) {
            return 50000; // 50秒堆箱
        } else if ("RETRIEVE".equals(operationType)) {
            return 45000; // 45秒取箱
        }
        return 40000; // 40秒默认
    }

    private long estimateITOperationTime(String operationType) {
        // 集卡操作时间估算（主要是等待吊具操作的时间）
        if ("LOAD".equals(operationType)) {
            return 15000; // 15秒装车
        } else if ("UNLOAD".equals(operationType)) {
            return 10000; // 10秒卸车
        }
        return 5000; // 5秒默认
    }

    public void setPortMap(PortMap portMap) {
        this.portMap = portMap;
    }
}
