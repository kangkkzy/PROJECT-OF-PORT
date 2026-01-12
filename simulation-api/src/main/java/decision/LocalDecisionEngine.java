package decision;

import entity.Entity;
import Instruction.Instruction;
import map.PortMap;
import map.Segment; // 需要导入 Segment
import java.util.*;

/**
 * 本地决策引擎实现
 * [修正] 这是一个"外部算法模块"的模拟实现
 * 它现在负责：1. 路径计算(BFS) 2. 简单的冲突记录 3. 任务分配
 */
public class LocalDecisionEngine implements ExternalTaskService {
    private Map<String, List<Instruction>> instructionQueues;
    private Map<String, Instruction> assignedInstructions;
    private PortMap portMap;

    public LocalDecisionEngine(PortMap portMap) {
        this.portMap = portMap;
        this.instructionQueues = new HashMap<>();
        this.instructionQueues.put("QC", new ArrayList<>());
        this.instructionQueues.put("YC", new ArrayList<>());
        this.instructionQueues.put("IT", new ArrayList<>());
        this.instructionQueues.put("UNKNOWN", new ArrayList<>());
        this.assignedInstructions = new HashMap<>();
    }

    @Override
    public void submitTask(Instruction instruction) {
        if (instruction.getTargetQC() != null) {
            instructionQueues.get("QC").add(instruction);
        } else if (instruction.getTargetYC() != null) {
            instructionQueues.get("YC").add(instruction);
        } else if (instruction.getTargetIT() != null) {
            instructionQueues.get("IT").add(instruction);
        } else {
            System.err.println("警告: 任务 " + instruction.getInstructionId() + " 未指定目标设备，存入待定队列。");
            instructionQueues.get("UNKNOWN").add(instruction);
        }

        instructionQueues.values().forEach(queue ->
                queue.sort(Comparator.comparingInt(Instruction::getPriority).reversed())
        );
    }

    @Override
    public Instruction askForNewTask(Entity entity) {
        List<Instruction> queue = instructionQueues.get(entity.getType().name());
        if (queue == null || queue.isEmpty()) return null;

        for (Instruction instruction : queue) {
            if (assignedInstructions.containsKey(instruction.getInstructionId())) continue;

            if (isEntitySuitableForInstruction(entity, instruction)) {
                assignedInstructions.put(instruction.getInstructionId(), instruction);
                instruction.assignToIT(entity.getId()); // 标记分配
                return instruction;
            }
        }
        return null;
    }

    @Override
    public void reportTaskCompletion(String instructionId, String entityId) {
        if (assignedInstructions.containsKey(instructionId)) {
            assignedInstructions.remove(instructionId);
            removeFromQueue(instructionId);
        }
    }

    // =========================================================
    // [核心修正 1] 实现 reportCollision 接口 (替代原来的 resolveCollision)
    // =========================================================
    @Override
    public void reportCollision(String entityId, String segmentId) {
        // 作为外部系统，这里只是简单记录日志
        // 在更复杂的实现中，这里可能会触发重新调度或发送 WAIT 指令
        System.out.println("!!! 外部决策系统收到冲突报警: 实体[" + entityId + "] 在路段[" + segmentId + "] 发生拥堵");
    }

    // =========================================================
    // [核心修正 2] 实现 getRoute 接口，并自己实现 BFS 寻路算法
    // 因为 PortMap 现在是纯数据容器，不提供算法，所以算法必须在这里实现
    // =========================================================
    @Override
    public List<String> getRoute(String origin, String destination) {
        if (origin.equals(destination)) return Collections.emptyList();

        // 1. 构建临时邻接表 (为了性能，实际项目中应缓存这个图)
        Map<String, List<String>> adjGraph = buildAdjacencyGraph();

        // 2. BFS 搜索
        Queue<String> queue = new LinkedList<>();
        Map<String, String> previous = new HashMap<>(); // 用于回溯路径: NodeId -> PreviousNodeId
        Set<String> visited = new HashSet<>();

        queue.add(origin);
        visited.add(origin);
        previous.put(origin, null);

        String current = null;
        boolean found = false;

        while (!queue.isEmpty()) {
            current = queue.poll();

            if (current.equals(destination)) {
                found = true;
                break;
            }

            List<String> neighbors = adjGraph.getOrDefault(current, Collections.emptyList());
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    previous.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        if (!found) {
            System.err.println("决策系统: 无法找到从 " + origin + " 到 " + destination + " 的路径");
            return Collections.emptyList();
        }

        // 3. 重建路径 (获取节点列表)
        List<String> pathNodes = new ArrayList<>();
        String node = destination;
        while (node != null) {
            pathNodes.add(0, node);
            node = previous.get(node);
        }

        // 4. 将节点列表转换为路段ID列表 (Segment IDs)
        List<String> routeSegmentIds = new ArrayList<>();
        for (int i = 0; i < pathNodes.size() - 1; i++) {
            String from = pathNodes.get(i);
            String to = pathNodes.get(i + 1);
            Segment seg = portMap.getSegmentBetween(from, to);
            if (seg != null) {
                routeSegmentIds.add(seg.getId());
            }
        }

        return routeSegmentIds;
    }

    /**
     * 辅助方法：从 PortMap 的路段数据构建简单的邻接表
     */
    private Map<String, List<String>> buildAdjacencyGraph() {
        Map<String, List<String>> graph = new HashMap<>();
        for (Segment seg : portMap.getAllSegments()) {
            // 正向连接
            graph.computeIfAbsent(seg.getFromNodeId(), k -> new ArrayList<>()).add(seg.getToNodeId());

            // 如果不是单行道，添加反向连接
            if (!seg.isOneWay()) {
                graph.computeIfAbsent(seg.getToNodeId(), k -> new ArrayList<>()).add(seg.getFromNodeId());
            }
        }
        return graph;
    }

    private void removeFromQueue(String instructionId) {
        for (List<Instruction> queue : instructionQueues.values()) {
            queue.removeIf(i -> i.getInstructionId().equals(instructionId));
        }
    }

    private boolean isEntitySuitableForInstruction(Entity entity, Instruction instruction) {
        switch (entity.getType()) {
            case QC: return entity.getId().equals(instruction.getTargetQC());
            case YC: return entity.getId().equals(instruction.getTargetYC());
            case IT: return entity.getId().equals(instruction.getTargetIT());
            default: return false;
        }
    }
}