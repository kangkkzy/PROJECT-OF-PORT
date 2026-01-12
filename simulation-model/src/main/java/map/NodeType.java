package map;

public enum NodeType {
    BAY("贝位"),
    QUAY("岸边"),
    ROAD("道路"),
    INTERSECTION("路口"),
    PARKING("停车区");

    private final String chineseName;

    NodeType(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public boolean isBay() {
        return this == BAY;
    }

    public boolean isQuay() {
        return this == QUAY;
    }

    public boolean isRoad() {
        return this == ROAD;
    }
}