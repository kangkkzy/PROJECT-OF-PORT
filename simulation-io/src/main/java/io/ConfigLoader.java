package io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;

public class ConfigLoader {
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
    public record StrategySettings(
            String routePlannerClass,
            String taskDispatcherClass,
            String taskGeneratorClass,
            String validatorClass,   // 新增
            String analyzerClass     // 新增
    ) {}

    public AppConfig load(String path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
        return mapper.readValue(new File(path), AppConfig.class);
    }
}