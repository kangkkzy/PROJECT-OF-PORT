package map;

import java.util.Objects;

/**
 * 坐标对象，用于替代原本的 "x_y" 字符串。
 */
public class Location {
    public final int x;
    public final int y;

    public Location(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // 辅助方法：从旧的字符串格式解析 (如 "10_20")
    public static Location parse(String key) {
        if (key == null || key.isEmpty()) return null;
        String[] parts = key.split("_");
        return new Location(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    // 转换为旧的 Key 格式 (为了兼容日志输出)
    public String toKey() {
        return x + "_" + y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return x == location.x && y == location.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}