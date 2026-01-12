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
import decision.ExternalTaskService;
import decision.LocalDecisionEngine;
import decision.RoutePlanner;
import decision.TaskDispatcher;      // [新增]
import algo.BfsRoutePlanner;
import algo.FifoTaskDispatcher;    // [新增]

import java.util.*;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("启动港口仿真系统 (全接口化版)...");

            // 1-5. 加载配置、地图、实体、任务 (保持不变)
            String configFile = getConfigFilePath(args);
            ConfigLoader configLoader = new ConfigLoader();
            SimulationConfig simConfig = configLoader.loadConfig(configFile);

            JsonMapLoader mapLoader = new JsonMapLoader();
            PortMap portMap = mapLoader.loadFromFile(simConfig.mapFile); //
            Map<String, Node> nodeMap = createNodeMap(portMap);

            EntityLoader entityLoader = new EntityLoader();
            List<Entity> entities = entityLoader.loadFromFile(simConfig.entityFile); //

            TaskLoader taskLoader = new TaskLoader();
            List<Instruction> tasks = taskLoader.loadFromFile(simConfig.taskFile, nodeMap); //

            SimulationEngine.SimulationConfig engineConfig = new SimulationEngine.SimulationConfig();
            engineConfig.setSimulationDuration(simConfig.endTime);
            engineConfig.setMaxEvents(simConfig.maxEvents);
            engineConfig.setTimeStep(simConfig.timeStep);

            // === 核心架构修正区 ===

            // A. 实例化 路由算法 (RoutePlanner)
            RoutePlanner routePlanner = new BfsRoutePlanner(portMap);

            // B. [新增] 实例化 任务调度算法 (TaskDispatcher)
            // 以后你想换成智能调度，只需要改这里，比如 new SmartGeneticDispatcher()
            TaskDispatcher taskDispatcher = new FifoTaskDispatcher();

            // C. 实例化决策引擎，注入 两个 算法接口
            ExternalTaskService taskService = new LocalDecisionEngine(routePlanner, taskDispatcher);

            // D. 实例化仿真引擎
            SimulationEngine engine = new SimulationEngine(portMap, engineConfig, taskService);

            // 6-7. 注册与启动 (保持不变)
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

    // 辅助方法保持不变
    private static String getConfigFilePath(String[] args) {
        if (args.length > 0) return args[0];
        return "config/simulation-config.json";
    }

    private static Map<String, Node> createNodeMap(PortMap portMap) {
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : portMap.getAllNodes()) {
            nodeMap.put(node.getId(), node);
        }
        return nodeMap;
    }
}