package decision;
// 路径规划接口
import java.util.List;

public interface RoutePlanner {

    List<String> searchRoute(String originId, String destinationId);
}