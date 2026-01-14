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
    private int taskCounter = 1000; // 自动生成的任务ID从1000开始

    // 构造函数接收上下文信息，由 Main 在加载时注入
    public RandomTaskGenerator(GridMap map, List<Entity> entities) {
        // 1. 分类实体
        for (Entity e : entities) {
            if (e.getType() == EntityType.QC) qcIds.add(e.getId());
            else if (e.getType() == EntityType.YC) ycIds.add(e.getId());
            else if (e.getType() == EntityType.IT) itIds.add(e.getId());
        }

        // 2. 识别地图上的关键节点 (简单硬编码识别，实际项目应从 map 获取)
        quayNodes.add("QUAY_01");
        quayNodes.add("QUAY_02");
        bayNodes.add("BAY_A01");
        bayNodes.add("BAY_A02");
        bayNodes.add("BAY_B01");
    }

    @Override
    public Instruction generate(long currentTime) {
        // 简单的随机逻辑：有50%概率不生成任务，避免任务积压过快
        if (random.nextDouble() > 0.5) return null;
        if (qcIds.isEmpty() || itIds.isEmpty() || bayNodes.isEmpty()) return null;

        String id = "AUTO_TASK_" + (taskCounter++);

        // 随机选择作业流向：装船 (BAY -> QUAY)
        String origin = bayNodes.get(random.nextInt(bayNodes.size()));
        String dest = quayNodes.get(random.nextInt(quayNodes.size()));

        Instruction task = new Instruction(id, InstructionType.LOAD_TO_SHIP, origin, dest);

        // 随机指派设备 (模拟 TOS 系统的预指派)
        task.setTargetQC(qcIds.get(random.nextInt(qcIds.size())));
        // 如果没有 YC，则为 null
        task.setTargetYC(ycIds.isEmpty() ? null : ycIds.get(random.nextInt(ycIds.size())));
        task.setTargetIT(itIds.get(random.nextInt(itIds.size())));

        task.setPriority(random.nextInt(3) + 1);
        task.setGenerateTime(java.time.Instant.ofEpochMilli(currentTime));
        // 随机作业时长 15-30秒
        task.setExpectedDuration(15000 + random.nextInt(15000));

        return task;
    }
}
