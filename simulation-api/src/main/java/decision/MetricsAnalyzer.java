package decision;

import event.SimEvent;
import java.util.List;
import java.util.Map;

/**
 * 指标分析器插件接口
 * 解决问题 2：如何将事件转化为算法迭代的依据？
 */
public interface MetricsAnalyzer {
    /**
     * 将原始事件流分析为业务指标报告
     * @param events 全量事件日志
     * @return 结构化的 KPI 数据（如吞吐量、利用率等）
     */
    Map<String, Object> analyze(List<SimEvent> events);
}