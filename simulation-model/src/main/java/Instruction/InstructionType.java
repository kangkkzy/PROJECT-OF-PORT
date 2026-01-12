package Instruction;

public enum InstructionType {
    LOAD_TO_SHIP("装船"),      // 从堆场装船
    UNLOAD_FROM_SHIP("卸船"),  // 从船卸到堆场
    YARD_TO_YARD("场内倒箱"),   // 场内箱位调整
    WAIT("等待"),              // 等待指令
    MOVE("移动");              // 空载移动

    private final String chineseName;

    InstructionType(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public boolean isLoadOperation() {
        return this == LOAD_TO_SHIP;
    }

    public boolean isUnloadOperation() {
        return this == UNLOAD_FROM_SHIP;
    }

    public boolean isMoveOperation() {
        return this == MOVE;
    }

    public boolean isWaitOperation() {
        return this == WAIT;
    }
}
