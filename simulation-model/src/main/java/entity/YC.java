package entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class YC extends Entity {
    private double maxLiftWeight;
    private double maxSpeed;
    private double acceleration;
    private double deceleration;
    private double baseCycleTime;
    private double timePerTier;

    public YC() { super(); }

    @JsonProperty("parameters")
    private void unpackParameters(Map<String, Object> props) {
        this.maxLiftWeight = toDouble(props.get("maxLiftWeight"));
        this.maxSpeed = toDouble(props.get("maxSpeed"));
        this.acceleration = toDouble(props.get("acceleration"));
        this.deceleration = toDouble(props.get("deceleration"));
        this.baseCycleTime = toDouble(props.get("baseCycleTime"));
        this.timePerTier = toDouble(props.get("timePerTier"));
    }

    private double toDouble(Object val) {
        return (val instanceof Number n) ? n.doubleValue() : 0.0;
    }

    @Override public double getMaxSpeed() { return maxSpeed; }
    @Override public double getAcceleration() { return acceleration; }
    @Override public double getDeceleration() { return deceleration; }

    public double calculateCycleTime(int tier) {
        return baseCycleTime + (tier * timePerTier);
    }
}