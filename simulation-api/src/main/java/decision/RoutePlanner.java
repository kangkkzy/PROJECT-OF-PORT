package decision;

import map.Location;
import java.util.List;

public interface RoutePlanner {
    // 接口升级：直接操作 Location
    List<Location> searchRoute(Location origin, Location destination);

    // 兼容重载 (可选，方便根据 ID 查询)
    List<Location> searchRoute(String originId, String destinationId);
}