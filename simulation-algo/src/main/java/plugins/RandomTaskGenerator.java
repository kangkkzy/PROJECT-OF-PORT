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

    // 从地图动态获取
    private final List<String> bayNodes;
    private final List<String> quayNodes;

    private final Random random = new Random(12345);
    private int taskCounter = 1;

    public RandomTaskGenerator(GridMap map, List<Entity> entities) {
        for (Entity e : entities) {
            if (e.getType() == EntityType.QC) qcIds.add(e.getId());
            else if (e.getType() == EntityType.YC) ycIds.add(e.getId());
            else if (e.getType() == EntityType.IT) itIds.add(e.getId());
        }

        // 修复：使用动态获取的节点
        this.quayNodes = map != null ? map.getNodesByType("QUAY") : new ArrayList<>();
        this.bayNodes = map != null ? map.getNodesByType("BAY") : new ArrayList<>();

        System.out.println("TaskGenerator Ready: QCs=" + qcIds.size() + ", YCs=" + ycIds.size() +
                ", ITs=" + itIds.size() + ", Bays=" + bayNodes.size() + ", Quays=" + quayNodes.size());
    }

    @Override
    public Instruction generate(long currentTime) {
        if (random.nextDouble() > 0.6) return null;
        if (qcIds.isEmpty() || itIds.isEmpty() || bayNodes.isEmpty() || quayNodes.isEmpty()) return null;

        String id = "AUTO_TASK_" + (taskCounter++);

        String origin = bayNodes.get(random.nextInt(bayNodes.size()));
        String dest = quayNodes.get(random.nextInt(quayNodes.size()));

        Instruction task = new Instruction(id, InstructionType.LOAD_TO_SHIP, origin, dest);

        task.setTargetQC(qcIds.get(random.nextInt(qcIds.size())));
        task.setTargetYC(ycIds.isEmpty() ? null : ycIds.get(random.nextInt(ycIds.size())));
        task.setTargetIT(itIds.get(random.nextInt(itIds.size())));

        task.setPriority(random.nextInt(3) + 1);
        task.setGenerateTime(currentTime);
        task.setExpectedDuration(15000 + random.nextInt(15000));

        return task;
    }
}