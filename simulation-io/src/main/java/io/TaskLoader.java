package io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import Instruction.Instruction;
import Instruction.InstructionType;
import map.GridMap; // 核心修改：引入 GridMap
import java.io.File;
import java.time.Instant;
import java.util.*;

public class TaskLoader {
    private final ObjectMapper objectMapper;
    private static final String KEY_ID = "id";
    private static final String KEY_TYPE = "type";
    private static final String KEY_ORIGIN = "origin";
    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_CONTAINER_ID = "containerId";
    private static final String KEY_CONTAINER_WEIGHT = "containerWeight";
    private static final String KEY_TARGET_QC = "targetQC";
    private static final String KEY_TARGET_YC = "targetYC";
    private static final String KEY_TARGET_IT = "targetIT";
    private static final String KEY_PRIORITY = "priority";
    private static final String KEY_GENERATE_TIME = "generateTime";
    private static final String KEY_PARAMETERS = "parameters";
    private static final String KEY_EXPECTED_DURATION = "expectedDuration";

    public TaskLoader() {
        this.objectMapper = new ObjectMapper();
    }

    public List<Instruction> loadFromFile(String filePath, GridMap gridMap) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("任务文件不存在: " + filePath);
        }

        JsonNode root = objectMapper.readTree(file);
        return parseTasks(root, gridMap);
    }

    private List<Instruction> parseTasks(JsonNode root, GridMap gridMap) {
        List<Instruction> tasks = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode taskObj : root) {
                Instruction task = parseTask(taskObj, gridMap);
                if (task != null) {
                    tasks.add(task);
                }
            }
        }

        // 按生成时间排序
        tasks.sort(Comparator.comparing(Instruction::getGenerateTime));

        return tasks;
    }

    private Instruction parseTask(JsonNode taskObj, GridMap gridMap) {
        //  解析基础必填信息
        String taskId = taskObj.get(KEY_ID).asText();
        String typeStr = taskObj.get(KEY_TYPE).asText();
        String originNodeId = taskObj.get(KEY_ORIGIN).asText();
        String destinationNodeId = taskObj.get(KEY_DESTINATION).asText();

        InstructionType type = parseInstructionType(typeStr);

        //   校验节点是否存在于 GridMap 中
        if (gridMap.getNodePosition(originNodeId) == null) {
            throw new IllegalArgumentException("起始节点不存在(未在地图定义): " + originNodeId);
        }
        if (gridMap.getNodePosition(destinationNodeId) == null) {
            throw new IllegalArgumentException("目标节点不存在(未在地图定义): " + destinationNodeId);
        }

        Instruction task = new Instruction(taskId, type, originNodeId, destinationNodeId);

        // 解析可选业务字段
        if (taskObj.has(KEY_CONTAINER_ID)) {
            task.setContainerId(taskObj.get(KEY_CONTAINER_ID).asText());
        }

        if (taskObj.has(KEY_CONTAINER_WEIGHT)) {
            task.setContainerWeight(taskObj.get(KEY_CONTAINER_WEIGHT).asDouble());
        }

        if (taskObj.has(KEY_TARGET_QC)) {
            task.setTargetQC(taskObj.get(KEY_TARGET_QC).asText());
        }

        if (taskObj.has(KEY_TARGET_YC)) {
            task.setTargetYC(taskObj.get(KEY_TARGET_YC).asText());
        }

        if (taskObj.has(KEY_TARGET_IT)) {
            task.setTargetIT(taskObj.get(KEY_TARGET_IT).asText());
        }

        if (taskObj.has(KEY_PRIORITY)) {
            task.setPriority(taskObj.get(KEY_PRIORITY).asInt());
        }

        if (taskObj.has(KEY_GENERATE_TIME)) {
            long generateTime = taskObj.get(KEY_GENERATE_TIME).asLong();
            task.setGenerateTime(Instant.ofEpochMilli(generateTime));
        }

        // 解析预期耗时
        if (taskObj.has(KEY_EXPECTED_DURATION)) {
            task.setExpectedDuration(taskObj.get(KEY_EXPECTED_DURATION).asLong());
        }

        // 解析通用参数
        if (taskObj.has(KEY_PARAMETERS)) {
            JsonNode paramsNode = taskObj.get(KEY_PARAMETERS);
            Map<String, Object> parameters = parseParameters(paramsNode);
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                task.setExtraParameter(entry.getKey(), entry.getValue());
            }
        }

        return task;
    }

    // 辅助方法 - 解析参数 Map
    private Map<String, Object> parseParameters(JsonNode paramsNode) {
        Map<String, Object> parameters = new HashMap<>();
        if (paramsNode != null && paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isNumber()) {
                    if (value.isInt()) {
                        parameters.put(entry.getKey(), value.asInt());
                    } else if (value.isDouble()) {
                        parameters.put(entry.getKey(), value.asDouble());
                    } else if (value.isLong()) {
                        parameters.put(entry.getKey(), value.asLong());
                    }
                } else if (value.isBoolean()) {
                    parameters.put(entry.getKey(), value.asBoolean());
                } else if (value.isTextual()) {
                    parameters.put(entry.getKey(), value.asText());
                }
            });
        }
        return parameters;
    }

    // 辅助方法 - 解析枚举类型
    private InstructionType parseInstructionType(String typeStr) {
        try {
            return InstructionType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的任务类型: " + typeStr);
        }
    }
}