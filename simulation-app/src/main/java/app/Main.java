package app;

import algo.SimpleScheduler;
import core.SimulationEngine;
import decision.RoutePlanner;
import decision.TaskAllocator;   // 新增
import decision.TaskGenerator;
import decision.TrafficController; // 新增
import entity.Entity;
import Instruction.Instruction;
import io.ConfigLoader;
import io.ConfigLoader.SimulationConfig;
import io.EntityLoader;
import io.JsonMapLoader;
import io.LogWriter;
import io.TaskLoader;
import map.GridMap;
import physics.PhysicsEngine;
import plugins.PhysicsTimeEstimator;
import time.TimeEstimationModule;

import java.lang.reflect.Constructor;
import java.util.List;

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

            // --- 核心修改：加载并转型 TaskDispatcher ---
            // 1. 先作为 Object 加载实现类 (FifoTaskDispatcher)
            Object dispatcherImpl = loadStrategy(simConfig.taskDispatcherClass, Object.class, null, null);

            // 2. 检查它是否同时实现了两个新接口
            if (!(dispatcherImpl instanceof TaskAllocator) || !(dispatcherImpl instanceof TrafficController)) {
                throw new IllegalArgumentException("配置的 taskDispatcherClass 必须同时实现 TaskAllocator 和 TrafficController 接口");
            }

            // 3. 拆分引用
            TaskAllocator taskAllocator = (TaskAllocator) dispatcherImpl;
            TrafficController trafficController = (TrafficController) dispatcherImpl;

            TaskGenerator taskGenerator = null;
            if (simConfig.taskGeneratorClass != null) {
                taskGenerator = loadStrategy(simConfig.taskGeneratorClass, TaskGenerator.class,
                        new Class<?>[]{GridMap.class, List.class},
                        new Object[]{gridMap, entities});
            }

            // --- 核心修改：使用新的 7 参数构造函数 ---
            SimpleScheduler scheduler = new SimpleScheduler(
                    taskAllocator,
                    trafficController,
                    routePlanner,
                    timeModule,
                    physics,
                    gridMap,
                    taskGenerator
            );

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

        // 修改检查逻辑：允许加载 Object 类型，或者严格检查接口实现
        if (interfaceType != Object.class && !interfaceType.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("类 " + className + " 未实现接口 " + interfaceType.getName());
        }

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