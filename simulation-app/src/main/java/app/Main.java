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
import decision.LocalDecisionEngine; // 引入本地实现

import java.util.*;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("启动港口仿真系统 (接口分离版)...");

            // 1-5. 加载配置、地图、实体、任务 (保持不变)
            String configFile = getConfigFilePath(args);
            ConfigLoader configLoader = new ConfigLoader();
            SimulationConfig simConfig = configLoader.loadConfig(configFile);

            JsonMapLoader mapLoader = new JsonMapLoader();
            PortMap portMap = mapLoader.loadFromFile(simConfig.mapFile);
            Map<String, Node> nodeMap = createNodeMap(portMap);

            EntityLoader entityLoader = new EntityLoader();
            List<Entity> entities = entityLoader.loadFromFile(simConfig.entityFile);

            TaskLoader taskLoader = new TaskLoader();
            List<Instruction> tasks = taskLoader.loadFromFile(simConfig.taskFile, nodeMap);

            // 6. 配置引擎
            SimulationEngine.SimulationConfig engineConfig = new SimulationEngine.SimulationConfig();
            engineConfig.setSimulationDuration(simConfig.endTime);
            engineConfig.setMaxEvents(simConfig.maxEvents);
            engineConfig.setTimeStep(simConfig.timeStep);

            // === 关键修改 ===
            // 实例化任务决策服务。
            // 这里我们使用 LocalDecisionEngine。如果你将来写好了 RemoteDecisionAdapter，改这里即可。
            ExternalTaskService taskService = new LocalDecisionEngine();

            // 将 taskService 传入 Engine
            SimulationEngine engine = new SimulationEngine(portMap, engineConfig, taskService);

            // 7. 注册数据
            for (Entity entity : entities) {
                engine.addEntity(entity);
            }
            for (Instruction task : tasks) {
                engine.addInstruction(task);
            }

            // 8. 运行
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