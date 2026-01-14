package event;

public enum EventType {
    // 系统内生事件
    TASK_GENERATION("新任务生成"), // 仿真驱动的任务产生
    MOVE_STEP("移动步进"),         // 用于Grid模式下的每一步移动

    // 桥吊事件
    QC_EXECUTION_COMPLETE("桥吊执行完成"),
    QC_ARRIVAL("桥吊到达目标位置"),

    // 龙门吊事件
    YC_EXECUTION_COMPLETE("龙门吊执行完成"),
    YC_ARRIVAL("龙门吊到达目标位置"),

    // 集卡事件
    IT_EXECUTION_COMPLETE("集卡执行完成"),
    IT_ARRIVAL("集卡到达目标位置");

    private final String chineseName;

    EventType(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public boolean isArrivalEvent() {
        return this == QC_ARRIVAL || this == YC_ARRIVAL || this == IT_ARRIVAL;
    }
}