package app;

import algo.SimpleScheduler;
import entity.Entity;
import Instruction.Instruction;
import map.GridMap;
import io.JsonMapLoader;
import io.EntityLoader;
import io.TaskLoader;
import io.ConfigLoader;
import io.ConfigLoader.SimulationConfig;
import io.LogWriter; // 新增引用
import core.SimulationEngine;
import physics.PhysicsEngine;
import plugins.PhysicsTimeEstimator;
import time.TimeEstimationModule;
import decision.ExternalTaskService;
import decision.LocalDecisionEngine;
import decision.RoutePlanner;
import decision.TaskDispatcher;
import decision.TaskGenerator;

import java.lang.reflect.Constructor;
import java.util.*;

public class Main {
    private static final String DEFAULT_CONFIG_PATH = "config/simulation-config.json";

    public static void main(String[] args) {
        try {
            System.out.println("启动港口仿真系统 (自驱动事件模式)...");

            ConfigLoader configLoader = new ConfigLoader();
            SimulationConfig simConfig = configLoader.loadConfig(getConfigFilePath(args));

            JsonMapLoader mapLoader = new JsonMapLoader();
            GridMap gridMap = mapLoader.loadGridMap(simConfig.mapFile, simConfig.cellSize);
            System.out.println("地图加载完成: " + gridMap.getWidth() + "x" + gridMap.getHeight());

            EntityLoader entityLoader = new EntityLoader();
            List<Entity> entities = entityLoader.loadFromFile(simConfig.entityFile);

            TaskLoader taskLoader = new TaskLoader();
            List<Instruction> initialTasks = taskLoader.loadFromFile(simConfig.taskFile, gridMap);

            PhysicsEngine physics = new PhysicsEngine(gridMap);
            TimeEstimationModule timeModule = new PhysicsTimeEstimator(gridMap);

            RoutePlanner routePlanner = loadStrategy(simConfig.routePlannerClass, RoutePlanner.class, new Class<?>[]{GridMap.class}, new Object[]{gridMap});
            TaskDispatcher taskDispatcher = loadStrategy(simConfig.taskDispatcherClass, TaskDispatcher.class, null, null);

            TaskGenerator taskGenerator = null;
            if (simConfig.taskGeneratorClass != null) {
                taskGenerator = loadStrategy(simConfig.taskGeneratorClass, TaskGenerator.class,
                        new Class<?>[]{GridMap.class, List.class},
                        new Object[]{gridMap, entities});
            }

            ExternalTaskService taskService = new LocalDecisionEngine(routePlanner, taskDispatcher);
            SimpleScheduler scheduler = new SimpleScheduler(taskService, timeModule, physics, gridMap, taskGenerator);

            SimulationEngine.SimulationConfig engineConfig = new SimulationEngine.SimulationConfig();
            engineConfig.setSimulationDuration(simConfig.endTime);
            engineConfig.setMaxEvents(simConfig.maxEvents);
            engineConfig.setTimeStep(simConfig.timeStep);

            SimulationEngine engine = new SimulationEngine(engineConfig, scheduler);

            for (Entity entity : entities) engine.addEntity(entity);
            for (Instruction task : initialTasks) engine.addInstruction(task);

            scheduler.init();

            Thread simThread = new Thread(() -> {
                try {
                    engine.start();
                    engine.generateReport();

                    // 【新增】仿真结束后，将日志落库
                    LogWriter logWriter = new LogWriter();
                    logWriter.writeLog(engine.getEventLog(), simConfig.logDir);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            simThread.start();
            simThread.join();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getConfigFilePath(String[] args) { return args.length > 0 ? args[0] : DEFAULT_CONFIG_PATH; }

    @SuppressWarnings("unchecked")
    private static <T> T loadStrategy(String className, Class<T> interfaceType, Class<?>[] paramTypes, Object[] args) throws Exception {
        if (className == null || className.isEmpty()) return null;
        Class<?> clazz = Class.forName(className);
        if (!interfaceType.isAssignableFrom(clazz)) throw new IllegalArgumentException("类 " + className + " 未实现接口 " + interfaceType.getName());
        try {
            if (paramTypes != null && args != null) {
                Constructor<?> constructor = clazz.getConstructor(paramTypes);
                return (T) constructor.newInstance(args);
            } else {
                return (T) clazz.getDeclaredConstructor().newInstance();
            }
        } catch (NoSuchMethodException e) {
            return (T) clazz.getDeclaredConstructor().newInstance();
        }
    }
}