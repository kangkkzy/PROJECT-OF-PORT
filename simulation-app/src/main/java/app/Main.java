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
// [新增] 导入接口和具体算法实现
import decision.RoutePlanner;
import algo.BfsRoutePlanner;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("启动港口仿真系统 (架构修正版)...");

            // 1. 加载配置
            String configFile = getConfigFilePath(args);
            ConfigLoader configLoader = new ConfigLoader();
            SimulationConfig simConfig = configLoader.loadConfig(configFile);

            // 2. 加载地图
            JsonMapLoader mapLoader = new JsonMapLoader();
            PortMap portMap = mapLoader.loadFromFile(simConfig.mapFile);
            Map<String, Node> nodeMap = createNodeMap(portMap);

            // 3. 加载实体
            EntityLoader entityLoader = new EntityLoader();
            List<Entity> entities = entityLoader.loadFromFile(simConfig.entityFile);

            // 4. 加载任务
            TaskLoader taskLoader = new TaskLoader();
            List<Instruction> tasks = taskLoader.loadFromFile(simConfig.taskFile, nodeMap);

            // 5. 配置引擎参数
            SimulationEngine.SimulationConfig engineConfig = new SimulationEngine.SimulationConfig();
            engineConfig.setSimulationDuration(simConfig.endTime);
            engineConfig.setMaxEvents(simConfig.maxEvents);
            engineConfig.setTimeStep(simConfig.timeStep);

            // === 核心架构修正区 ===

            // A. 实例化具体的算法模块 (BfsRoutePlanner)
            // 这里我们决定使用 BFS 算法，也可以换成 DijkstraRoutePlanner 等
            RoutePlanner routePlanner = new BfsRoutePlanner(portMap);

            // B. 实例化决策引擎，注入算法接口
            // LocalDecisionEngine 现在是一个纯粹的管理者，不知道具体的寻路细节
            ExternalTaskService taskService = new LocalDecisionEngine(routePlanner);

            // C. 实例化仿真引擎
            SimulationEngine engine = new SimulationEngine(portMap, engineConfig, taskService);

            // 6. 注册实体和任务
            for (Entity entity : entities) {
                engine.addEntity(entity);
            }
            for (Instruction task : tasks) {
                engine.addInstruction(task);
            }

            // 7. 启动
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