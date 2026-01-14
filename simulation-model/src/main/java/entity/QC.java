package entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class QC extends Entity {
    private double maxLiftWeight;
    private double spreaderWidth;
    private double maxSpeed;
    private double acceleration;
    private double deceleration;
    private double baseLiftTime;
    private double timePerMeter;

    public QC() { super(); }

    // 核心黑科技：自动解包 parameters 字段到当前对象属性
    @JsonProperty("parameters")
    private void unpackParameters(Map<String, Object> props) {
        this.maxLiftWeight = toDouble(props.get("maxLiftWeight"));
        this.spreaderWidth = toDouble(props.get("spreaderWidth"));
        this.maxSpeed = toDouble(props.get("maxSpeed"));
        this.acceleration = toDouble(props.get("acceleration"));
        this.deceleration = toDouble(props.get("deceleration"));
        this.baseLiftTime = toDouble(props.get("baseLiftTime"));
        this.timePerMeter = toDouble(props.get("timePerMeter"));
    }

    private double toDouble(Object val) {
        return (val instanceof Number n) ? n.doubleValue() : 0.0;
    }

    @Override public double getMaxSpeed() { return maxSpeed; }
    @Override public double getAcceleration() { return acceleration; }
    @Override public double getDeceleration() { return deceleration; }

    public double calculateLiftTime(double height) {
        return baseLiftTime + (height * timePerMeter);
    }
}