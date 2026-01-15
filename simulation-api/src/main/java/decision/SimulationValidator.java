package decision;

import event.SimEvent;
import java.util.List;

public interface SimulationValidator {
    /**
     * 校验仿真产生的事件流是否符合物理与业务逻辑
     * @param events 仿真产生的全量事件日志
     * @return 错误或警告信息列表，为空表示通过
     */
    List<String> validate(List<SimEvent> events);
}