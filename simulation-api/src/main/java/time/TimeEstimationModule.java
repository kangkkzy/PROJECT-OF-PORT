package time;

import entity.Entity;
import java.util.List;
// 时间估算接口

public interface TimeEstimationModule {

    long estimateMovementTime(Entity entity, List<String> pathIds);
}