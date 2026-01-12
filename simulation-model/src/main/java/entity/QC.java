package entity;

public class QC extends Entity {
    private double maxLiftWeight;
    private double spreaderWidth;

    public QC(String id, String initialPosition, double maxLiftWeight, double spreaderWidth) {
        super(id, EntityType.QC, initialPosition);
        this.maxLiftWeight = maxLiftWeight;
        this.spreaderWidth = spreaderWidth;
    }

    @Override
    public double getMaxSpeed() {
        return 1.5; // 桥吊移动速度较慢，1.5 m/s
    }

    @Override
    public double getAcceleration() {
        return 0.3; // m/s²
    }

    @Override
    public double getDeceleration() {
        return 0.4; // m/s²
    }

    public double getMaxLiftWeight() {
        return maxLiftWeight;
    }

    public double getSpreaderWidth() {
        return spreaderWidth;
    }

    public double calculateLiftTime(double height) {
        // 吊箱时间 = 基础时间 + 高度相关时间
        return 30.0 + (height / 1.5); // 30秒基础时间 + 高度/1.5
    }
}
