package time;

import entity.Entity;
import map.Location;
import java.util.List;

public interface TimeEstimationModule {
    // 接口签名已更新，接受 List<Location>
    long estimateMovementTime(Entity entity, List<Location> pathIds);
}