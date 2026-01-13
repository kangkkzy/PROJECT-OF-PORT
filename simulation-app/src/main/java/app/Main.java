package app;

import entity.Entity;
import Instruction.Instruction;
import map.PortMap;
import map.Node;
import io.JsonMapLoader;
import io.EntityLoader;
import io.TaskLoader;
import io.ConfigLoader;
import io.ConfigLoader.SimulationConfig;
import core.SimulationEngine;
import physics.PhysicsEngine;
import algo.SimpleScheduler;
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
            System.out.println("启动港口仿真系统 (算法模块化版)...");

            // 加载配置
            String configFile = getConfigFilePath(args);
            ConfigLoader configLoader = new ConfigLoader();
            SimulationConfig simConfig = configLoader.loadConfig(configFile);

            //  加载地图
            JsonMapLoader mapLoader = new JsonMapLoader();
            PortMap portMap = mapLoader.loadFromFile(simConfig.mapFile);
            Map<String, Node> nodeMap = createNodeMap(portMap);

            //  加载实体
            EntityLoader entityLoader = new EntityLoader();
            List<Entity> entities = entityLoader.loadFromFile(simConfig.entityFile);

            //   加载任务
            TaskLoader taskLoader = new TaskLoader();
            List<Instruction> tasks = taskLoader.loadFromFile(simConfig.taskFile, nodeMap);

            //   配置引擎参数
            SimulationEngine.SimulationConfig engineConfig = new SimulationEngine.SimulationConfig();
            engineConfig.setSimulationDuration(simConfig.endTime);
            engineConfig.setMaxEvents(simConfig.maxEvents);
            engineConfig.setTimeStep(simConfig.timeStep);

            //   初始化核心组件
            PhysicsEngine physics = new PhysicsEngine();

            // 注入具体的算法实现
            TimeEstimationModule timeModule = new PhysicsTimeEstimator(portMap);

            //  加载策略算法 (路径规划 & 任务调度)
            RoutePlanner routePlanner = loadStrategy(
                    simConfig.routePlannerClass,
                    RoutePlanner.class,
                    new Class<?>[]{PortMap.class},
                    new Object[]{portMap}
            );

            TaskDispatcher taskDispatcher = loadStrategy(
                    simConfig.taskDispatcherClass,
                    TaskDispatcher.class,
                    null,
                    null
            );

            System.out.println("已加载策略: " + routePlanner.getClass().getSimpleName() +
                    " & " + taskDispatcher.getClass().getSimpleName());

            //  组装并启动
            ExternalTaskService taskService = new LocalDecisionEngine(routePlanner, taskDispatcher);
            SimpleScheduler scheduler = new SimpleScheduler(taskService, timeModule, physics, portMap);

            SimulationEngine engine = new SimulationEngine(portMap, engineConfig, scheduler, physics);

            for (Entity entity : entities) {
                engine.addEntity(entity);
            }
            for (Instruction task : tasks) {
                engine.addInstruction(task);
            }

            engine.start();
            engine.generateReport();

        } catch (Exception e) {
            System.err.println("仿真发生严重错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getConfigFilePath(String[] args) {
        if (args.length > 0) return args[0];
        return DEFAULT_CONFIG_PATH;
    }

    private static Map<String, Node> createNodeMap(PortMap portMap) {
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : portMap.getAllNodes()) {
            nodeMap.put(node.getId(), node);
        }
        return nodeMap;
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadStrategy(String className, Class<T> interfaceType, Class<?>[] paramTypes, Object[] args) throws Exception {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("配置文件中缺少必需的算法类名");
        }
        Class<?> clazz = Class.forName(className);
        if (!interfaceType.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("类 " + className + " 没有实现接口 " + interfaceType.getName());
        }
        if (paramTypes != null && args != null) {
            Constructor<?> constructor = clazz.getConstructor(paramTypes);
            return (T) constructor.newInstance(args);
        } else {
            return (T) clazz.getDeclaredConstructor().newInstance();
        }
    }
}