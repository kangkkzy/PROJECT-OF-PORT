package io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;

public class ConfigLoader {
    private final ObjectMapper objectMapper;

    public ConfigLoader() {
        this.objectMapper = new ObjectMapper();
    }

    public SimulationConfig loadConfig(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("配置文件不存在: " + filePath);
        }

        JsonNode root = objectMapper.readTree(file);
        return parseConfig(root);
    }

    private SimulationConfig parseConfig(JsonNode root) {
        SimulationConfig config = new SimulationConfig();

        if (root.has("simulation")) {
            JsonNode simNode = root.get("simulation");
            if (simNode.has("name")) config.name = simNode.get("name").asText();
        }

        if (root.has("timeSettings")) {
            JsonNode timeNode = root.get("timeSettings");
            if (timeNode.has("startTime")) config.startTime = timeNode.get("startTime").asLong();
            if (timeNode.has("endTime")) config.endTime = timeNode.get("endTime").asLong();
            if (timeNode.has("timeStep")) config.timeStep = timeNode.get("timeStep").asLong();
            if (timeNode.has("maxEvents")) config.maxEvents = timeNode.get("maxEvents").asInt();
        }

        // 读取地图参数
        if (root.has("mapSettings")) {
            JsonNode mapNode = root.get("mapSettings");
            if (mapNode.has("cellSize")) config.cellSize = mapNode.get("cellSize").asDouble();
        }

        if (root.has("paths")) {
            JsonNode pathsNode = root.get("paths");
            if (pathsNode.has("mapFile")) config.mapFile = pathsNode.get("mapFile").asText();
            if (pathsNode.has("taskFile")) config.taskFile = pathsNode.get("taskFile").asText();
            if (pathsNode.has("entityFile")) config.entityFile = pathsNode.get("entityFile").asText();
        }

        if (root.has("strategies")) {
            JsonNode stratNode = root.get("strategies");
            if (stratNode.has("routePlannerClass"))
                config.routePlannerClass = stratNode.get("routePlannerClass").asText();
            if (stratNode.has("taskDispatcherClass"))
                config.taskDispatcherClass = stratNode.get("taskDispatcherClass").asText();
        }

        return config;
    }

    public static class SimulationConfig {
        public String name;
        public long startTime = 0;
        public long endTime = 86400000;
        public long timeStep = 1000;
        public int maxEvents = 1000000;

        // 默认网格大小 1.0 米
        public double cellSize = 1.0;

        public String mapFile;
        public String taskFile;
        public String entityFile;
        public String routePlannerClass;
        public String taskDispatcherClass;
    }
}