package plugins;

import decision.TaskGenerator;
import entity.Entity;
import entity.EntityType;
import Instruction.Instruction;
import Instruction.InstructionType;
import map.GridMap;

import java.util.*;

public class RandomTaskGenerator implements TaskGenerator {
    private final List<Entity> qcs = new ArrayList<>();
    private final List<Entity> ycs = new ArrayList<>();
    private final List<Entity> its = new ArrayList<>();

    private final GridMap gridMap;
    private final Random random = new Random(12345);
    private int taskCounter = 1;

    public RandomTaskGenerator(GridMap map, List<Entity> entities) {
        this.gridMap = map;
        for (Entity e : entities) {
            if (e.getType() == EntityType.QC) qcs.add(e);
            else if (e.getType() == EntityType.YC) ycs.add(e);
            else if (e.getType() == EntityType.IT) its.add(e);
        }

        System.out.println("TaskGenerator 初始化: QCs=" + qcs.size() + ", YCs=" + ycs.size() + ", ITs=" + its.size());
    }

    @Override
    public Instruction generate(long currentTime) {
        // 控制任务生成频率，避免一开始生成太多导致拥堵
        if (random.nextDouble() > 0.6) return null;
        if (qcs.isEmpty() || its.isEmpty() || ycs.isEmpty()) return null;

        String id = "AUTO_TASK_" + (taskCounter++);

        // 核心修复：基于设备实体生成任务，确保物理位置匹配
        Entity targetYC = ycs.get(random.nextInt(ycs.size()));
        Entity targetQC = qcs.get(random.nextInt(qcs.size()));
        Entity targetIT = its.get(random.nextInt(its.size()));

        // 获取设备所在的节点ID作为任务的起止点
        String origin = getEntityNodeId(targetYC);
        String destination = getEntityNodeId(targetQC);

        // 如果设备尚未初始化位置，则跳过本次生成
        if (origin == null || destination == null) {
            return null;
        }

        Instruction task = new Instruction(id, InstructionType.LOAD_TO_SHIP, origin, destination);

        task.setTargetQC(targetQC.getId());
        task.setTargetYC(targetYC.getId());
        task.setTargetIT(targetIT.getId());

        task.setPriority(random.nextInt(3) + 1);
        task.setGenerateTime(currentTime);
        task.setExpectedDuration(15000 + random.nextInt(15000));
        task.setContainerWeight(20.0 + random.nextDouble() * 20.0);

        return task;
    }

    private String getEntityNodeId(Entity e) {
        // 1. 优先尝试获取当前位置的 ID
        if (e.getCurrentLocation() != null) {
            String nodeId = gridMap.getNodeId(e.getCurrentLocation());
            if (nodeId != null) return nodeId;
        }
        // 2. 回退到初始位置配置
        return e.getInitialNodeId();
    }
}