import java.io.File;
import java.io.InputStream;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigLoader {
    private ObjectMapper objectMapper;

    public ConfigLoader() {
        this.objectMapper = new ObjectMapper();
    }

    public SimulationConfig loadConfig(String filePath) throws Exception {
        File file = new File(filePath);
        JsonNode root = objectMapper.readTree(file);

        SimulationConfig config = new SimulationConfig();

        // 加载基本配置
        config.setSimulationDuration(root.path("simulationDuration").asLong(86400000)); // 24小时
        config.setTimeStep(root.path("timeStep").asLong(1000)); // 1秒
        config.setMaxEvents(root.path("maxEvents").asInt(1000000));
        config.setEnableLogging(root.path("enableLogging").asBoolean(true));
        config.setLogLevel(root.path("logLevel").asText("INFO"));

        // 加载地图配置
        config.setMapFile(root.path("mapFile").asText("config/map.json"));
        config.setTaskFile(root.path("taskFile").asText("config/tasks.json"));
        config.setEntityFile(root.path("entityFile").asText("config/entities.json"));

        // 加载仿真参数
        JsonNode paramsNode = root.path("parameters");
        if (paramsNode.isObject()) {
            Map<String, Object> parameters = new HashMap<>();
            paramsNode.fields().forEachRemaining(entry -> {
                parameters.put(entry.getKey(), entry.getValue());
            });
            config.setParameters(parameters);
        }

        return config;
    }

    public List<EntityConfig> loadEntities(String filePath) throws Exception {
        File file = new File(filePath);
        JsonNode root = objectMapper.readTree(file);

        List<EntityConfig> entities = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode entityJson : root) {
                EntityConfig entityConfig = new EntityConfig();
                entityConfig.setId(entityJson.path("id").asText());
                entityConfig.setType(entityJson.path("type").asText());
                entityConfig.setInitialPosition(entityJson.path("initialPosition").asText());

                JsonNode paramsNode = entityJson.path("parameters");
                if (paramsNode.isObject()) {
                    Map<String, Object> params = new HashMap<>();
                    paramsNode.fields().forEachRemaining(entry -> {
                        params.put(entry.getKey(), entry.getValue());
                    });
                    entityConfig.setParameters(params);
                }

                entities.add(entityConfig);
            }
        }

        return entities;
    }

    public List<InstructionConfig> loadInstructions(String filePath) throws Exception {
        File file = new File(filePath);
        JsonNode root = objectMapper.readTree(file);

        List<InstructionConfig> instructions = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode instrJson : root) {
                InstructionConfig instrConfig = new InstructionConfig();
                instrConfig.setId(instrJson.path("id").asText());
                instrConfig.setType(instrJson.path("type").asText());
                instrConfig.setOrigin(instrJson.path("origin").asText());
                instrConfig.setDestination(instrJson.path("destination").asText());
                instrConfig.setContainerId(instrJson.path("containerId").asText());
                instrConfig.setContainerWeight(instrJson.path("containerWeight").asDouble(20.0));
                instrConfig.setTargetQC(instrJson.path("targetQC").asText());
                instrConfig.setTargetYC(instrJson.path("targetYC").asText());
                instrConfig.setTargetIT(instrJson.path("targetIT").asText());
                instrConfig.setPriority(instrJson.path("priority").asInt(1));
                instrConfig.setGenerateTime(instrJson.path("generateTime").asLong(0));

                instructions.add(instrConfig);
            }
        }

        // 按生成时间排序
        instructions.sort(Comparator.comparingLong(InstructionConfig::getGenerateTime));

        return instructions;
    }

    // 配置类
    public static class SimulationConfig {
        private long simulationDuration;
        private long timeStep;
        private int maxEvents;
        private boolean enableLogging;
        private String logLevel;
        private String mapFile;
        private String taskFile;
        private String entityFile;
        private Map<String, Object> parameters;

        // Getters and Setters
        public long getSimulationDuration() { return simulationDuration; }
        public void setSimulationDuration(long simulationDuration) { this.simulationDuration = simulationDuration; }

        public long getTimeStep() { return timeStep; }
        public void setTimeStep(long timeStep) { this.timeStep = timeStep; }

        public int getMaxEvents() { return maxEvents; }
        public void setMaxEvents(int maxEvents) { this.maxEvents = maxEvents; }

        public boolean isEnableLogging() { return enableLogging; }
        public void setEnableLogging(boolean enableLogging) { this.enableLogging = enableLogging; }

        public String getLogLevel() { return logLevel; }
        public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

        public String getMapFile() { return mapFile; }
        public void setMapFile(String mapFile) { this.mapFile = mapFile; }

        public String getTaskFile() { return taskFile; }
        public void setTaskFile(String taskFile) { this.taskFile = taskFile; }

        public String getEntityFile() { return entityFile; }
        public void setEntityFile(String entityFile) { this.entityFile = entityFile; }

        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }

    public static class EntityConfig {
        private String id;
        private String type;
        private String initialPosition;
        private Map<String, Object> parameters;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getInitialPosition() { return initialPosition; }
        public void setInitialPosition(String initialPosition) { this.initialPosition = initialPosition; }

        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }

    public static class InstructionConfig {
        private String id;
        private String type;
        private String origin;
        private String destination;
        private String containerId;
        private double containerWeight;
        private String targetQC;
        private String targetYC;
        private String targetIT;
        private int priority;
        private long generateTime;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getOrigin() { return origin; }
        public void setOrigin(String origin) { this.origin = origin; }

        public String getDestination() { return destination; }
        public void setDestination(String destination) { this.destination = destination; }

        public String getContainerId() { return containerId; }
        public void setContainerId(String containerId) { this.containerId = containerId; }

        public double getContainerWeight() { return containerWeight; }
        public void setContainerWeight(double containerWeight) { this.containerWeight = containerWeight; }

        public String getTargetQC() { return targetQC; }
        public void setTargetQC(String targetQC) { this.targetQC = targetQC; }

        public String getTargetYC() { return targetYC; }
        public void setTargetYC(String targetYC) { this.targetYC = targetYC; }

        public String getTargetIT() { return targetIT; }
        public void setTargetIT(String targetIT) { this.targetIT = targetIT; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public long getGenerateTime() { return generateTime; }
        public void setGenerateTime(long generateTime) { this.generateTime = generateTime; }
    }
}
