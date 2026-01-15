package decision;

import event.SimEvent;
import java.util.List;

/**
 * 仿真逻辑校验器插件接口
 * 解决问题 1：如何确认仿真结果是正确的？
 */
public interface SimulationValidator {
    /**
     * 对生成的事件流进行逻辑合法性校验
     * @param events 全量事件日志
     * @return 校验报告（包含发现的错误或警告）
     */
    List<String> validate(List<SimEvent> events);
}