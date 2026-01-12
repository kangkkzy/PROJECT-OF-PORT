import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import Instruction.Instruction;
import Instruction.InstructionType;
import map.Node;
import java.io.File;
import java.time.Instant;
import java.util.*;

/**
 * 纯粹的JSON任务加载器
 * 只负责从JSON文件加载任务配置
 */
public class TaskLoader {
    private final ObjectMapper objectMapper;

    public TaskLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从JSON文件加载任务
     */
    public List<Instruction> loadFromFile(String filePath, Map<String, Node> nodeMap) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("任务文件不存在: " + filePath);
        }

        JsonNode root = objectMapper.readTree(file);
        return parseTasks(root, nodeMap);
    }

    /**
     * 解析任务数组
     */
    private List<Instruction> parseTasks(JsonNode root, Map<String, Node> nodeMap) {
        List<Instruction> tasks = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode taskObj : root) {
                Instruction task = parseTask(taskObj, nodeMap);
                if (task != null) {
                    tasks.add(task);
                }
            }
        }

        // 按生成时间排序
        tasks.sort(Comparator.comparing(Instruction::getGenerateTime));

        return tasks;
    }

    /**
     * 解析单个任务
     */
    private Instruction parseTask(JsonNode taskObj, Map<String, Node> nodeMap) {
        String taskId = taskObj.get("id").asText();
        String typeStr = taskObj.get("type").asText();
        String originNodeId = taskObj.get("origin").asText();
        String destinationNodeId = taskObj.get("destination").asText();

        // 解析任务类型
        InstructionType type = parseInstructionType(typeStr);

        // 验证节点存在性
        Node origin = nodeMap.get(originNodeId);
        if (origin == null) {
            throw new IllegalArgumentException("起始节点不存在: " + originNodeId);
        }

        Node destination = nodeMap.get(destinationNodeId);
        if (destination == null) {
            throw new IllegalArgumentException("目标节点不存在: " + destinationNodeId);
        }

        // 创建任务
        Instruction task = new Instruction(taskId, type, origin, destination);

        // 设置可选字段
        if (taskObj.has("containerId")) {
            task.setContainerId(taskObj.get("containerId").asText());
        }

        if (taskObj.has("containerWeight")) {
            task.setContainerWeight(taskObj.get("containerWeight").asDouble());
        }

        if (taskObj.has("targetQC")) {
            task.setTargetQC(taskObj.get("targetQC").asText());
        }

        if (taskObj.has("targetYC")) {
            task.setTargetYC(taskObj.get("targetYC").asText());
        }

        if (taskObj.has("targetIT")) {
            task.setTargetIT(taskObj.get("targetIT").asText());
        }

        if (taskObj.has("priority")) {
            task.setPriority(taskObj.get("priority").asInt());
        }

        if (taskObj.has("generateTime")) {
            long generateTime = taskObj.get("generateTime").asLong();
            task.setGenerateTime(Instant.ofEpochMilli(generateTime));
        }

        return task;
    }

    /**
     * 解析任务类型
     */
    private InstructionType parseInstructionType(String typeStr) {
        try {
            return InstructionType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的任务类型: " + typeStr);
        }
    }
}