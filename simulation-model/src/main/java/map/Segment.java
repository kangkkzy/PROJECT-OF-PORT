package map;

public class Segment {
    private String id;
    private String fromNodeId;
    private String toNodeId;
    private double length;
    private double maxSpeed;
    private boolean isOneWay;

    // 通过json提供所有的数据
    public Segment(String id, String fromNodeId, String toNodeId, double length, double maxSpeed, boolean isOneWay) {
        this.id = id;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.length = length;
        this.maxSpeed = maxSpeed;
        this.isOneWay = isOneWay;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public void setFromNodeId(String fromNodeId) {
        this.fromNodeId = fromNodeId;
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(String toNodeId) {
        this.toNodeId = toNodeId;
    }

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public boolean isOneWay() {
        return isOneWay;
    }

    public void setOneWay(boolean oneWay) {
        isOneWay = oneWay;
    }

    // 路段是否正确
    public boolean connects(String nodeId1, String nodeId2) {
        // 如果是单行道，必须方向匹配 (From -> To)
        // 如果是双行道，方向可以互换
        if (isOneWay) {
            return fromNodeId.equals(nodeId1) && toNodeId.equals(nodeId2);
        } else {
            return (fromNodeId.equals(nodeId1) && toNodeId.equals(nodeId2)) ||
                    (fromNodeId.equals(nodeId2) && toNodeId.equals(nodeId1));
        }
    }

    @Override
    public String toString() {
        return String.format("路段[%s] 从%s到%s 长度:%.2f 限速:%.1f %s",
                id, fromNodeId, toNodeId, length, maxSpeed,
                isOneWay ? "(单行道)" : "(双行道)");
    }
}