package io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;

public class ConfigLoader {
    // 使用 Record 定义配置结构，自动匹配 JSON
    public record AppConfig(
            SimulationConfig simulation,
            TimeSettings timeSettings,
            MapSettings mapSettings,
            OutputSettings output,
            PathSettings paths,
            StrategySettings strategies
    ) {}

    public record SimulationConfig(String name) {}
    public record TimeSettings(long startTime, long endTime, long timeStep, int maxEvents) {}
    public record MapSettings(double cellSize) {}
    public record OutputSettings(String logDir) {}
    public record PathSettings(String mapFile, String taskFile, String entityFile) {}
    public record StrategySettings(String routePlannerClass, String taskDispatcherClass, String taskGeneratorClass) {}

    public AppConfig load(String path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(new File(path), AppConfig.class);
    }
}