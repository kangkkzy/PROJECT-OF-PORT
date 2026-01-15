package decision;

import event.SimEvent;
import java.util.List;
import java.util.Map;

public interface MetricsAnalyzer {
    /**
     * 将事件流分析为关键绩效指标 (KPI)
     * @param events 仿真产生的全量事件日志
     * @return KPI 指标名称与数值的映射
     */
    Map<String, Object> analyze(List<SimEvent> events);
}