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
        System.out.println("TaskGenerator Ready: QCs=" + qcs.size() + ", YCs=" + ycs.size() + ", ITs=" + its.size());
    }

    @Override
    public Instruction generate(long currentTime) {
        if (qcs.isEmpty() || its.isEmpty() || ycs.isEmpty()) return null;
        if (random.nextDouble() > 0.3) return null; // 70% 概率生成

        String id = "AUTO_TASK_" + (taskCounter++);

        Entity targetYC = ycs.get(random.nextInt(ycs.size()));
        Entity targetQC = qcs.get(random.nextInt(qcs.size()));
        Entity targetIT = its.get(random.nextInt(its.size()));

        String originNodeId = getEntityNodeId(targetYC);
        String destNodeId = getEntityNodeId(targetQC);

        if (originNodeId == null || destNodeId == null) return null;

        Instruction task = new Instruction(id, InstructionType.LOAD_TO_SHIP, originNodeId, destNodeId);
        task.setTargetQC(targetQC.getId());
        task.setTargetYC(targetYC.getId());
        task.setTargetIT(targetIT.getId());

        task.setPriority(random.nextInt(3) + 1);
        task.setGenerateTime(currentTime);
        task.setExpectedDuration(15000);
        task.setContainerWeight(20.0);

        return task;
    }

    private String getEntityNodeId(Entity e) {
        if (e.getCurrentLocation() != null) {
            String nodeId = gridMap.getNodeId(e.getCurrentLocation());
            if (nodeId != null) return nodeId;
        }
        return e.getInitialNodeId();
    }
}