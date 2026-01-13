package io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;

public class ConfigLoader {
    private final ObjectMapper objectMapper;

    public ConfigLoader() {
        this.objectMapper = new ObjectMapper();
    }

    // 从json中加载配置
    public SimulationConfig loadConfig(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("配置文件不存在: " + filePath);
        }

        JsonNode root = objectMapper.readTree(file);
        return parseConfig(root);
    }

    // 解析配置json
    private SimulationConfig parseConfig(JsonNode root) {
        SimulationConfig config = new SimulationConfig();

        //  基本配置
        if (root.has("simulation")) {
            JsonNode simNode = root.get("simulation");
            if (simNode.has("name")) config.name = simNode.get("name").asText();
        }

        //   时间设置
        if (root.has("timeSettings")) {
            JsonNode timeNode = root.get("timeSettings");
            if (timeNode.has("startTime")) config.startTime = timeNode.get("startTime").asLong();
            if (timeNode.has("endTime")) config.endTime = timeNode.get("endTime").asLong();
            if (timeNode.has("timeStep")) config.timeStep = timeNode.get("timeStep").asLong();
            if (timeNode.has("maxEvents")) config.maxEvents = timeNode.get("maxEvents").asInt();
        }

        //   文件路径
        if (root.has("paths")) {
            JsonNode pathsNode = root.get("paths");
            if (pathsNode.has("mapFile")) config.mapFile = pathsNode.get("mapFile").asText();
            if (pathsNode.has("taskFile")) config.taskFile = pathsNode.get("taskFile").asText();
            if (pathsNode.has("entityFile")) config.entityFile = pathsNode.get("entityFile").asText();
        }

        //  读取 JSON 中的节点
        if (root.has("strategies")) {
            JsonNode stratNode = root.get("strategies");
            if (stratNode.has("routePlannerClass"))
                config.routePlannerClass = stratNode.get("routePlannerClass").asText();
            if (stratNode.has("taskDispatcherClass"))
                config.taskDispatcherClass = stratNode.get("taskDispatcherClass").asText();
        }
        else {
            if (root.has("routePlannerClass")) config.routePlannerClass = root.get("routePlannerClass").asText();
            if (root.has("taskDispatcherClass")) config.taskDispatcherClass = root.get("taskDispatcherClass").asText();
        }

        return config;
    }

    // 配置类
    public static class SimulationConfig {
        public String name;
        public long startTime = 0;
        public long endTime = 86400000;
        public long timeStep = 1000;
        public int maxEvents = 1000000;

        public String mapFile;
        public String taskFile;
        public String entityFile;
        public String routePlannerClass;
        public String taskDispatcherClass;

        @Override
        public String toString() {
            return String.format("SimulationConfig[name=%s, duration=%dms, router=%s]",
                    name, endTime, routePlannerClass);
        }
    }
}