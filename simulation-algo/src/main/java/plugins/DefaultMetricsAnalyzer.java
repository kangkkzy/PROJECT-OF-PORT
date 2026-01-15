package plugins;

import decision.MetricsAnalyzer;
import event.SimEvent;
import event.EventType;
import java.util.*;

public class DefaultMetricsAnalyzer implements MetricsAnalyzer {
    @Override
    public Map<String, Object> analyze(List<SimEvent> events) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // 1. 吞吐量统计
        long throughput = events.stream()
                .filter(e -> e.getType() == EventType.QC_EXECUTION_COMPLETE)
                .count();
        metrics.put("Total_Throughput", throughput);

        // 2. 作业事件分布
        metrics.put("Simulation_Event_Count", events.size());

        return metrics;
    }
}