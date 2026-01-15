package app;

import algo.SimpleScheduler;
import core.SimulationEngine;
import decision.*;
import entity.Entity;
import Instruction.Instruction;
import io.*;
import map.GridMap;
import physics.PhysicsEngine;
import plugins.GridTimeEstimator;
import time.TimeEstimationModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            var loader = new ConfigLoader();
            String configPath = args.length > 0 ? args[0] : "config/simulation-config.json";
            var config = loader.load(configPath);

            GridMap gridMap = new JsonMapLoader().loadGridMap(config.paths().mapFile(), config.mapSettings().cellSize());
            List<Entity> entities = new EntityLoader().loadFromFile(config.paths().entityFile());
            List<Instruction> tasks = new TaskLoader().loadFromFile(config.paths().taskFile());

            PhysicsEngine physics = new PhysicsEngine(gridMap);
            TimeEstimationModule timeModule = new GridTimeEstimator(gridMap);

            RoutePlanner routePlanner = loadPlugin(config.strategies().routePlannerClass(), GridMap.class, gridMap);

            Object dispatcherObj = loadPlugin(config.strategies().taskDispatcherClass());
            TaskAllocator taskAllocator = (TaskAllocator) dispatcherObj;
            TrafficController trafficController = (TrafficController) dispatcherObj;

            TaskGenerator generator = loadPluginMulti(config.strategies().taskGeneratorClass(),
                    new Class<?>[]{GridMap.class, List.class}, new Object[]{gridMap, entities});

            SimulationValidator validator = loadPlugin(config.strategies().validatorClass());
            MetricsAnalyzer analyzer = loadPlugin(config.strategies().analyzerClass());

            SimpleScheduler scheduler = new SimpleScheduler(
                    taskAllocator, trafficController,
                    routePlanner, timeModule, physics, gridMap, generator
            );

            entities.forEach(scheduler::registerEntity);
            tasks.forEach(scheduler::addInstruction);

            scheduler.init();

            SimulationEngine engine = new SimulationEngine(config.timeSettings().endTime(), config.timeSettings().maxEvents(), scheduler);
            engine.setValidator(validator);
            engine.setAnalyzer(analyzer);

            engine.start();

            new LogWriter().writeLog(engine.getEventLog(), config.output().logDir());

        } catch (Exception e) {
            logger.error("仿真运行失败", e);
            e.printStackTrace();
        }
    }

    private static <T> T loadPlugin(String className) throws Exception {
        if (className == null || className.isEmpty()) return null;
        return (T) Class.forName(className).getDeclaredConstructor().newInstance();
    }

    private static <T> T loadPlugin(String className, Class<?> paramType, Object paramValue) throws Exception {
        if (className == null || className.isEmpty()) return null;
        try {
            return (T) Class.forName(className).getConstructor(paramType).newInstance(paramValue);
        } catch (NoSuchMethodException e) {
            return (T) Class.forName(className).getDeclaredConstructor().newInstance();
        }
    }

    private static <T> T loadPluginMulti(String className, Class<?>[] paramTypes, Object[] args) throws Exception {
        if (className == null || className.isEmpty()) return null;
        return (T) Class.forName(className).getConstructor(paramTypes).newInstance(args);
    }
}