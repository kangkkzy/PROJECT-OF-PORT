package plugins;

import decision.SimulationValidator;
import event.SimEvent;
import java.util.*;

public class DefaultValidator implements SimulationValidator {
    @Override
    public List<String> validate(List<SimEvent> events) {
        List<String> reports = new ArrayList<>();
        Map<String, Long> lastTime = new HashMap<>();

        for (SimEvent e : events) {
            // 物理校验：时间单调性
            long prev = lastTime.getOrDefault(e.getEntityId(), 0L);
            if (e.getTimestamp() < prev) {
                reports.add("[错误] 设备 " + e.getEntityId() + " 时间倒流: " + e.getTimestamp());
            }
            lastTime.put(e.getEntityId(), e.getTimestamp());

            // 业务校验可以按需添加：如到达后才能完成作业等
        }
        if (reports.isEmpty()) reports.add("[成功] 仿真逻辑一致性校验通过。");
        return reports;
    }
}