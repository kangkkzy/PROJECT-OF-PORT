package decision;

import java.util.List;
// 路径规划接口

public interface RoutePlanner {
    List<String> searchRoute(String originId, String destinationId);
}