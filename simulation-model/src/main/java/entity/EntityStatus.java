package entity;

public enum EntityStatus {
    IDLE("空闲"),
    MOVING("移动"),
    EXECUTING("执行"),
    WAITING("等待");

    private final String chineseName;

    EntityStatus(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public boolean isMoving() {
        return this == MOVING;
    }

    public boolean isExecuting() {
        return this == EXECUTING;
    }

    public boolean isWaiting() {
        return this == WAITING;
    }

    public boolean isIdle() {
        return this == IDLE;
    }
}