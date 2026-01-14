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
import core.SimulationEngine;
import physics.PhysicsEngine;
import plugins.PhysicsTimeEstimator;
import time.TimeEstimationModule;
import decision.ExternalTaskService;
import decision.LocalDecisionEngine;
import decision.RoutePlanner;
import decision.TaskDispatcher;

import java.lang.reflect.Constructor;
import java.util.*;

public class Main {

    private static final String DEFAULT_CONFIG_PATH = "config/simulation-config.json";

    public static void main(String[] args) {
        try {
            System.out.println("启动港口仿真系统 (Grid模式 + 物理时间估算)...");

            // 1. 加载配置
            ConfigLoader configLoader = new ConfigLoader();
            SimulationConfig simConfig = configLoader.loadConfig(getConfigFilePath(args));

            //  加载并光栅化地图
            JsonMapLoader mapLoader = new JsonMapLoader();
            GridMap gridMap = mapLoader.loadGridMap(simConfig.mapFile, simConfig.cellSize);
            System.out.println("地图加载完成: " + gridMap.getWidth() + "x" + gridMap.getHeight() +
                    " 格子大小:" + gridMap.getCellSize() + "m");

            //  加载实体
            EntityLoader entityLoader = new EntityLoader();
            List<Entity> entities = entityLoader.loadFromFile(simConfig.entityFile);

            //  加载任务
            TaskLoader taskLoader = new TaskLoader();
            List<Instruction> tasks = taskLoader.loadFromFile(simConfig.taskFile, gridMap);

            //  初始化核心物理与时间组件
            PhysicsEngine physics = new PhysicsEngine(gridMap);
            TimeEstimationModule timeModule = new PhysicsTimeEstimator(gridMap);

            //   动态加载决策策略
            RoutePlanner routePlanner = loadStrategy(
                    simConfig.routePlannerClass,
                    RoutePlanner.class,
                    new Class<?>[]{GridMap.class},
                    new Object[]{gridMap}
            );

            // TaskDispatcher 来构造函数
            TaskDispatcher taskDispatcher = loadStrategy(
                    simConfig.taskDispatcherClass,
                    TaskDispatcher.class,
                    null,
                    null
            );

            //   组装调度层
            ExternalTaskService taskService = new LocalDecisionEngine(routePlanner, taskDispatcher);
            SimpleScheduler scheduler = new SimpleScheduler(taskService, timeModule, physics, gridMap);

            //  组装仿真引擎
            SimulationEngine.SimulationConfig engineConfig = new SimulationEngine.SimulationConfig();
            engineConfig.setSimulationDuration(simConfig.endTime);
            engineConfig.setMaxEvents(simConfig.maxEvents);
            engineConfig.setTimeStep(simConfig.timeStep);

            SimulationEngine engine = new SimulationEngine(engineConfig, scheduler);

            //  注册对象到引擎
            for (Entity entity : entities) {
                engine.addEntity(entity);
            }
            for (Instruction task : tasks) {
                engine.addInstruction(task);
            }

            //  初始化调度（生成首批事件）
            scheduler.init();

            //  启动仿真线程
            Thread simThread = new Thread(() -> {
                try {
                    engine.start();
                    engine.generateReport();
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

    private static String getConfigFilePath(String[] args) {
        if (args.length > 0) return args[0];
        return DEFAULT_CONFIG_PATH;
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadStrategy(String className, Class<T> interfaceType, Class<?>[] paramTypes, Object[] args) throws Exception {
        if (className == null || className.isEmpty()) throw new IllegalArgumentException("策略类名配置为空");

        Class<?> clazz = Class.forName(className);
        if (!interfaceType.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("类 " + className + " 未实现接口 " + interfaceType.getName());
        }

        try {
            // 尝试使用带参数的构造函数
            if (paramTypes != null && args != null) {
                Constructor<?> constructor = clazz.getConstructor(paramTypes);
                return (T) constructor.newInstance(args);
            } else {
                return (T) clazz.getDeclaredConstructor().newInstance();
            }
        } catch (NoSuchMethodException e) {
            // 如果找不到带参构造函数 退化为默认无参构造
            return (T) clazz.getDeclaredConstructor().newInstance();
        }
    }
}