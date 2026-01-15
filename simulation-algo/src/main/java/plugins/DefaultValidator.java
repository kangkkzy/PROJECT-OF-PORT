package plugins;

import decision.SimulationValidator;
import event.SimEvent;
import event.EventType;
import java.util.*;

public class DefaultValidator implements SimulationValidator {
    @Override
    public List<String> validate(List<SimEvent> events) {
        List<String> reports = new ArrayList<>();
        Map<String, Long> lastEntityEventTime = new HashMap<>();

        for (SimEvent e : events) {
            // 校验点 1：时间线单调性检查
            long lastTime = lastEntityEventTime.getOrDefault(e.getEntityId(), 0L);
            if (e.getTimestamp() < lastTime) {
                reports.add("[错误] 实体 " + e.getEntityId() + " 发生时间倒流: " + e.getTimestamp());
            }
            lastEntityEventTime.put(e.getEntityId(), e.getTimestamp());

            // 校验点 2：业务时序检查（例如：完成事件必须在到达之后）
            // 这里可以根据业务需要扩展更复杂的逻辑
        }

        if (reports.isEmpty()) reports.add("[成功] 仿真物理逻辑自检通过");
        return reports;
    }
}
