package decision;

import java.util.List;

/**
 * 路由规划接口
 * 作用：将"决策"与"算法实现"解耦。
 * 决策引擎只知道需要"searchRoute"，不关心是 BFS、DFS 还是 A*。
 */
public interface RoutePlanner {

    /**
     * 规划路径
     * @param originId 起点ID
     * @param destinationId 终点ID
     * @return 路径的路段ID列表 (Segment IDs)
     */
    List<String> searchRoute(String originId, String destinationId);
}