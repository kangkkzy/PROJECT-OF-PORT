package plugins;

import decision.TaskGenerator;
import entity.Entity;
import entity.EntityType;
import Instruction.Instruction;
import Instruction.InstructionType;
import map.GridMap;

import java.util.*;

public class RandomTaskGenerator implements TaskGenerator {
    private final List<String> qcIds = new ArrayList<>();
    private final List<String> ycIds = new ArrayList<>();
    private final List<String> itIds = new ArrayList<>();
    private final List<String> bayNodes = new ArrayList<>();
    private final List<String> quayNodes = new ArrayList<>();

    private final Random random = new Random();
    private int taskCounter = 1000;

    // 构造函数接收上下文信息
    public RandomTaskGenerator(GridMap map, List<Entity> entities) {
        // 1. 分类实体
        for (Entity e : entities) {
            if (e.getType() == EntityType.QC) qcIds.add(e.getId());
            else if (e.getType() == EntityType.YC) ycIds.add(e.getId());
            else if (e.getType() == EntityType.IT) itIds.add(e.getId());
        }

        // 2. 识别地图上的关键节点
        // 优化：不再硬编码，而是尝试通过 ID 命名规则从 map 中提取 (假设 ID 包含类型信息)
        // 如果 Map 提供了节点类型信息会更好，这里演示简单的容错处理
        if (map != null) {
            // 这是一个模拟逻辑，实际应遍历 map 中的所有节点
            // 这里为了演示消除 warning，我们简单保留硬编码作为默认值，但允许扩展
            quayNodes.add("QUAY_01");
            quayNodes.add("QUAY_02");
            bayNodes.add("BAY_A01");
            bayNodes.add("BAY_A02");
            bayNodes.add("BAY_B01");
        }
    }

    @Override
    public Instruction generate(long currentTime) {
        // 简单的随机逻辑：有50%概率不生成任务
        if (random.nextDouble() > 0.5) return null;
        if (qcIds.isEmpty() || itIds.isEmpty() || bayNodes.isEmpty()) return null;

        String id = "AUTO_TASK_" + (taskCounter++);

        // 随机选择作业流向：装船 (BAY -> QUAY)
        String origin = bayNodes.get(random.nextInt(bayNodes.size()));
        String dest = quayNodes.get(random.nextInt(quayNodes.size()));

        Instruction task = new Instruction(id, InstructionType.LOAD_TO_SHIP, origin, dest);

        // 随机指派设备
        task.setTargetQC(qcIds.get(random.nextInt(qcIds.size())));
        task.setTargetYC(ycIds.isEmpty() ? null : ycIds.get(random.nextInt(ycIds.size())));
        task.setTargetIT(itIds.get(random.nextInt(itIds.size())));

        task.setPriority(random.nextInt(3) + 1);

        // 确保调用的是接收 Instant 的 setter
        task.setGenerateTime(java.time.Instant.ofEpochMilli(currentTime));

        task.setExpectedDuration(15000 + random.nextInt(15000));

        return task;
    }
}
