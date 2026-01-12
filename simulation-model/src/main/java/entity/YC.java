package entity;

public class YC extends Entity {
    private double maxLiftWeight;
    private double gantrySpeed;

    public YC(String id, String initialPosition, double maxLiftWeight, double gantrySpeed) {
        super(id, EntityType.YC, initialPosition);
        this.maxLiftWeight = maxLiftWeight;
        this.gantrySpeed = gantrySpeed;
    }

    @Override
    public double getMaxSpeed() {
        return gantrySpeed; // 龙门吊大车速度
    }

    @Override
    public double getAcceleration() {
        return 0.5; // m/s²
    }

    @Override
    public double getDeceleration() {
        return 0.6; // m/s²
    }

    public double getMaxLiftWeight() {
        return maxLiftWeight;
    }

    public double getGantrySpeed() {
        return gantrySpeed;
    }

    public double calculateCycleTime(int tier) {
        // 龙门吊作业周期 = 基础时间 + 层数相关时间
        return 45.0 + (tier * 2.0); // 45秒基础时间 + 每层2秒
    }
}