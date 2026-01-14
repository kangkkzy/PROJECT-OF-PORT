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

        SimulationConfig config = parseConfig(objectMapper.readTree(file));
        validate(config);
        return config;
    }

    private SimulationConfig parseConfig(JsonNode root) {
        SimulationConfig config = new SimulationConfig();

        if (root.has("simulation")) {
            JsonNode sim = root.get("simulation");
            if (sim.has("name")) config.name = sim.get("name").asText();
        }

        if (root.has("timeSettings")) {
            JsonNode time = root.get("timeSettings");
            if (time.has("startTime")) config.startTime = time.get("startTime").asLong();
            if (time.has("endTime")) config.endTime = time.get("endTime").asLong();
            if (time.has("maxEvents")) config.maxEvents = time.get("maxEvents").asInt();
            if (time.has("timeStep")) config.timeStep = time.get("timeStep").asLong();
        }

        if (root.has("mapSettings")) {
            if (root.get("mapSettings").has("cellSize")) {
                config.cellSize = root.get("mapSettings").get("cellSize").asDouble();
            }
        }

        // 【新增】解析输出配置
        if (root.has("output")) {
            if (root.get("output").has("logDir")) {
                config.logDir = root.get("output").get("logDir").asText();
            }
        }

        if (root.has("paths")) {
            JsonNode paths = root.get("paths");
            if (paths.has("mapFile")) config.mapFile = paths.get("mapFile").asText();
            if (paths.has("taskFile")) config.taskFile = paths.get("taskFile").asText();
            if (paths.has("entityFile")) config.entityFile = paths.get("entityFile").asText();
        }

        if (root.has("strategies")) {
            JsonNode strats = root.get("strategies");
            if (strats.has("routePlannerClass")) config.routePlannerClass = strats.get("routePlannerClass").asText();
            if (strats.has("taskDispatcherClass")) config.taskDispatcherClass = strats.get("taskDispatcherClass").asText();
            if (strats.has("taskGeneratorClass")) config.taskGeneratorClass = strats.get("taskGeneratorClass").asText();
        }

        return config;
    }

    private void validate(SimulationConfig config) {
        if (config.endTime <= 0) throw new IllegalArgumentException("配置文件错误: 必须指定有效的 'timeSettings.endTime'");
        if (config.maxEvents <= 0) throw new IllegalArgumentException("配置文件错误: 必须指定有效的 'timeSettings.maxEvents'");
        if (config.cellSize <= 0) throw new IllegalArgumentException("配置文件错误: 必须指定有效的 'mapSettings.cellSize'");

        // 【新增】校验输出路径
        if (config.logDir == null || config.logDir.isEmpty()) {
            throw new IllegalArgumentException("配置文件错误: 必须指定 'output.logDir' (日志输出目录)");
        }

        if (config.mapFile == null) throw new IllegalArgumentException("配置文件错误: 缺少 'paths.mapFile'");
        if (config.taskFile == null) throw new IllegalArgumentException("配置文件错误: 缺少 'paths.taskFile'");
        if (config.entityFile == null) throw new IllegalArgumentException("配置文件错误: 缺少 'paths.entityFile'");
        if (config.routePlannerClass == null) throw new IllegalArgumentException("配置文件错误: 缺少 'strategies.routePlannerClass'");
        if (config.taskDispatcherClass == null) throw new IllegalArgumentException("配置文件错误: 缺少 'strategies.taskDispatcherClass'");
        if (config.taskGeneratorClass == null) throw new IllegalArgumentException("配置文件错误: 缺少 'strategies.taskGeneratorClass'");
    }

    public static class SimulationConfig {
        public String name;
        public long startTime;
        public long endTime;
        public long timeStep;
        public int maxEvents;
        public double cellSize;

        public String logDir; // 新增字段

        public String mapFile;
        public String taskFile;
        public String entityFile;
        public String routePlannerClass;
        public String taskDispatcherClass;
        public String taskGeneratorClass;
    }
}