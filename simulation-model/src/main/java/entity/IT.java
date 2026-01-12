package entity;

public class IT extends Entity {
    private double maxLoadWeight;
    private double currentLoadWeight;

    public IT(String id, String initialPosition, double maxLoadWeight) {
        super(id, EntityType.IT, initialPosition);
        this.maxLoadWeight = maxLoadWeight;
        this.currentLoadWeight = 0.0;
    }

    @Override
    public double getMaxSpeed() {
        return 6.0; // 集卡速度 6 m/s
    }

    @Override
    public double getAcceleration() {
        return 0.8; // m/s²
    }

    @Override
    public double getDeceleration() {
        return 1.2; // m/s²
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