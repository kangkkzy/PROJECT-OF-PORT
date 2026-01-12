package io; // 新增

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;

/**
 * 纯粹的JSON配置加载器
 * 只负责从JSON文件加载仿真配置
 */
public class ConfigLoader {
    private final ObjectMapper objectMapper;

    public ConfigLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从JSON文件加载配置
     */
    public SimulationConfig loadConfig(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("配置文件不存在: " + filePath);
        }

        JsonNode root = objectMapper.readTree(file);
        return parseConfig(root);
    }

    /**
     * 解析配置JSON
     */
    private SimulationConfig parseConfig(JsonNode root) {
        SimulationConfig config = new SimulationConfig();

        // 基本配置
        if (root.has("simulation")) {
            JsonNode simNode = root.get("simulation");
            config.name = simNode.get("name").asText();
        }

        // 时间设置
        if (root.has("timeSettings")) {
            JsonNode timeNode = root.get("timeSettings");
            config.startTime = timeNode.get("startTime").asLong();
            config.endTime = timeNode.get("endTime").asLong();
            config.timeStep = timeNode.get("timeStep").asLong();
            config.maxEvents = timeNode.get("maxEvents").asInt();
        }

        // 文件路径
        if (root.has("paths")) {
            JsonNode pathsNode = root.get("paths");
            config.mapFile = pathsNode.get("mapFile").asText();
            config.taskFile = pathsNode.get("taskFile").asText();
            config.entityFile = pathsNode.get("entityFile").asText();
        }

        return config;
    }

    /**
     * 配置类
     */
    public static class SimulationConfig {
        public String name;
        public long startTime = 0;
        public long endTime = 86400000;
        public long timeStep = 1000;
        public int maxEvents = 1000000;
        public String mapFile;
        public String taskFile;
        public String entityFile;

        @Override
        public String toString() {
            return String.format("SimulationConfig[name=%s, duration=%dms]", name, endTime);
        }
    }
}