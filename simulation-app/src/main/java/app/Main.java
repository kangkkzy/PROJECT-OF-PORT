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
            System.out.println("启动港口仿真系统 (事件驱动修正版)...");

            ConfigLoader configLoader = new ConfigLoader();
            SimulationConfig simConfig = configLoader.loadConfig(getConfigFilePath(args));

            JsonMapLoader mapLoader = new JsonMapLoader();
            PortMap portMap = mapLoader.loadFromFile(simConfig.mapFile);
            Map<String, Node> nodeMap = createNodeMap(portMap);

            EntityLoader entityLoader = new EntityLoader();
            List<Entity> entities = entityLoader.loadFromFile(simConfig.entityFile);

            TaskLoader taskLoader = new TaskLoader();
            List<Instruction> tasks = taskLoader.loadFromFile(simConfig.taskFile, nodeMap);

            SimulationEngine.SimulationConfig engineConfig = new SimulationEngine.SimulationConfig();
            engineConfig.setSimulationDuration(simConfig.endTime);
            engineConfig.setMaxEvents(simConfig.maxEvents);
            engineConfig.setTimeStep(simConfig.timeStep);

            PhysicsEngine physics = new PhysicsEngine();
            TimeEstimationModule timeModule = new PhysicsTimeEstimator(portMap);

            RoutePlanner routePlanner = loadStrategy(simConfig.routePlannerClass, RoutePlanner.class, new Class<?>[]{PortMap.class}, new Object[]{portMap});
            TaskDispatcher taskDispatcher = loadStrategy(simConfig.taskDispatcherClass, TaskDispatcher.class, null, null);

            ExternalTaskService taskService = new LocalDecisionEngine(routePlanner, taskDispatcher);
            SimpleScheduler scheduler = new SimpleScheduler(taskService, timeModule, physics, portMap);
            SimulationEngine engine = new SimulationEngine(portMap, engineConfig, scheduler, physics);

            for (Entity entity : entities) {
                engine.addEntity(entity);
            }
            for (Instruction task : tasks) {
                engine.addInstruction(task);
            }

            // ==========================================
            // 【关键修改】初始化调度器：
            // 此时所有实体和指令已就位，必须显式触发一次分配，
            // 生成初始事件(Arrival/Wait等)放入队列，
            // 否则 engine.start() 会因队列为空直接结束。
            // ==========================================
            scheduler.init();

            // 启动仿真线程
            Thread simThread = new Thread(() -> {
                try {
                    engine.start();
                    engine.generateReport();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            simThread.start();

            // --- 演示逻辑：模拟外部干预 ---
            try {
                // 等待一段时间，让仿真跑起来
                Thread.sleep(2000);

                System.out.println("\n[Main] >>> 外部指令：紧急暂停 <<<");
                taskDispatcher.setEmergencyStop(true);

                // 暂停一段时间
                Thread.sleep(3000);

                System.out.println("\n[Main] >>> 外部指令：恢复运行 <<<");
                taskDispatcher.setEmergencyStop(false);

                simThread.join();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
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
        if (className == null || className.isEmpty()) throw new IllegalArgumentException("类名为空");
        Class<?> clazz = Class.forName(className);
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