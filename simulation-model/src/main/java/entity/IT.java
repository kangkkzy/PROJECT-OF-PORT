package entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class IT extends Entity {
    private double maxLoadWeight;
    private double currentLoadWeight;
    private double maxSpeed;
    private double acceleration;
    private double deceleration;

    public IT() { super(); }

    @JsonProperty("parameters")
    private void unpackParameters(Map<String, Object> props) {
        this.maxLoadWeight = toDouble(props.get("maxLoadWeight"));
        this.maxSpeed = toDouble(props.get("maxSpeed"));
        this.acceleration = toDouble(props.get("acceleration"));
        this.deceleration = toDouble(props.get("deceleration"));
    }

    private double toDouble(Object val) {
        return (val instanceof Number n) ? n.doubleValue() : 0.0;
    }

    @Override public double getMaxSpeed() { return maxSpeed; }
    @Override public double getAcceleration() { return acceleration; }
    @Override public double getDeceleration() { return deceleration; }

    public void setCurrentLoadWeight(double weight) { this.currentLoadWeight = weight; }
    public boolean isLoaded() { return currentLoadWeight > 0; }
    public void clearLoad() { this.currentLoadWeight = 0.0; }
}