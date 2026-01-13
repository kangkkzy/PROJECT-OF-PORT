package entity;

public class YC extends Entity {
    private double maxLiftWeight;

    // 物理属性
    private double maxSpeed;
    private double acceleration;
    private double deceleration;

    // 基础作业时间 耗时
    private double baseCycleTime;
    private double timePerTier;

    public YC(String id, String initialPosition, double maxLiftWeight,
              double maxSpeed, double acceleration, double deceleration,
              double baseCycleTime, double timePerTier) {
        super(id, EntityType.YC, initialPosition);
        this.maxLiftWeight = maxLiftWeight;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
        this.baseCycleTime = baseCycleTime;
        this.timePerTier = timePerTier;
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

    //使用配置参数计算作业时间
    public double calculateCycleTime(int tier) {
        return baseCycleTime + (tier * timePerTier);
    }
}