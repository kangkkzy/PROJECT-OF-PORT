package entity;

public enum EntityType {
    QC("桥吊"),
    YC("龙门吊"),
    IT("集卡");

    private final String chineseName;

    EntityType(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public static EntityType fromChineseName(String chineseName) {
        for (EntityType type : values()) {
            if (type.getChineseName().equals(chineseName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的设备类型: " + chineseName);
    }
}