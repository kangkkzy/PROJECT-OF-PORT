package plugins;

import decision.MetricsAnalyzer;
import event.SimEvent;
import event.EventType;
import java.util.*;

public class DefaultMetricsAnalyzer implements MetricsAnalyzer {
    @Override
    public Map<String, Object> analyze(List<SimEvent> events) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // 1. 吞吐量分析
        long totalTasks = events.stream()
                .filter(e -> e.getType() == EventType.QC_EXECUTION_COMPLETE)
                .count();
        metrics.put("Throughput_Total_QC_Completed", totalTasks);

        // 2. 设备活跃度分析
        Map<String, Long> eventCounts = new HashMap<>();
        for (SimEvent e : events) {
            eventCounts.put(e.getEntityId(), eventCounts.getOrDefault(e.getEntityId(), 0L) + 1);
        }
        metrics.put("Device_Activity_Count", eventCounts);

        // 3. 仿真规模
        metrics.put("Total_Event_Stream_Size", events.size());

        return metrics;
    }
}