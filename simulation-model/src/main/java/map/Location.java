package map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record Location(int x, int y) {
    @JsonCreator
    public static Location parse(String key) {
        if (key == null || key.isEmpty()) return null;
        String[] parts = key.split("_");
        return new Location(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    @JsonValue
    public String toKey() { return x + "_" + y; }

    @Override public String toString() { return "(" + x + "," + y + ")"; }
}