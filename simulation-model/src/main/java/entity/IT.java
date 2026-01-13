package entity;

public class IT extends Entity {
    private double maxLoadWeight;
    private double currentLoadWeight;

    private double maxSpeed;
    private double acceleration;
    private double deceleration;

    // 构造
    public IT(String id, String initialPosition, double maxLoadWeight,
              double maxSpeed, double acceleration, double deceleration) {
        super(id, EntityType.IT, initialPosition);
        this.maxLoadWeight = maxLoadWeight;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
        this.currentLoadWeight = 0.0;
    }

    //  直接返回配置值
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

    public double getMaxLoadWeight() {
        return maxLoadWeight;
    }

    public double getCurrentLoadWeight() {
        return currentLoadWeight;
    }

    public void setCurrentLoadWeight(double weight) {
        if (weight > maxLoadWeight) {
            throw new IllegalArgumentException("载荷超过最大承载量");
        }
        this.currentLoadWeight = weight;
    }

    public boolean isLoaded() {
        return currentLoadWeight > 0;
    }

    public void clearLoad() {
        this.currentLoadWeight = 0.0;
    }
}