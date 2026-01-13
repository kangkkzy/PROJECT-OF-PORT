package entity;

public class QC extends Entity {
    private double maxLiftWeight;
    private double spreaderWidth;

    //  物理属性
    private double maxSpeed;
    private double acceleration;
    private double deceleration;

    // 基础吊运时间 移动时间
    private double baseLiftTime;
    private double timePerMeter;

    public QC(String id, String initialPosition, double maxLiftWeight, double spreaderWidth,
              double maxSpeed, double acceleration, double deceleration,
              double baseLiftTime, double timePerMeter) {
        super(id, EntityType.QC, initialPosition);
        this.maxLiftWeight = maxLiftWeight;
        this.spreaderWidth = spreaderWidth;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
        this.baseLiftTime = baseLiftTime;
        this.timePerMeter = timePerMeter;
    }

    @Override
    public double getMaxSpeed() {
        return maxSpeed;
    }

    @Override
    public double getAcceleration() {
        return acceleration;
    }

    @Override
    public double getDeceleration() {
        return deceleration;
    }

    public double getMaxLiftWeight() {
        return maxLiftWeight;
    }

    public double getSpreaderWidth() {
        return spreaderWidth;
    }

    // 使用配置参数计算
    public double calculateLiftTime(double height) {
        return baseLiftTime + (height * timePerMeter);
    }
}
