package app; // 必须与文件夹名为 app 对应

import entity.Entity;
import Instruction.Instruction;
import map.PortMap;
import map.Node;
import io.JsonMapLoader;
import io.EntityLoader;
import io.TaskLoader;
import io.ConfigLoader;
import io.ConfigLoader.SimulationConfig;
import core.SimulationEngine; // 必须引用核心包

import java.util.*;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("启动港口仿真系统...");

            // 1. 加载配置文件
            String configFile = getConfigFilePath(args);
            ConfigLoader configLoader = new ConfigLoader();
            SimulationConfig simConfig = configLoader.loadConfig(configFile);
            System.out.println("加载配置文件: " + configFile);

            // 2. 加载地图
            System.out.println("加载地图文件: " + simConfig.mapFile);
            JsonMapLoader mapLoader = new JsonMapLoader();
            PortMap portMap = mapLoader.loadFromFile(simConfig.mapFile);
            System.out.println("地图加载成功: ID=" + portMap.getMapId() + ", 节点数=" + portMap.getNodeCount());

            // 3. 辅助节点映射
            Map<String, Node> nodeMap = createNodeMap(portMap);

            // 4. 加载实体
            System.out.println("加载实体文件: " + simConfig.entityFile);
            EntityLoader entityLoader = new EntityLoader();
            List<Entity> entities = entityLoader.loadFromFile(simConfig.entityFile);
            System.out.println("实体加载成功: 数量=" + entities.size());

            // 5. 加载任务
            System.out.println("加载任务文件: " + simConfig.taskFile);
            TaskLoader taskLoader = new TaskLoader();
            List<Instruction> tasks = taskLoader.loadFromFile(simConfig.taskFile, nodeMap);
            System.out.println("任务加载成功: 数量=" + tasks.size());

            // 6. 配置引擎
            SimulationEngine.SimulationConfig engineConfig = new SimulationEngine.SimulationConfig();
            engineConfig.setSimulationDuration(simConfig.endTime);
            engineConfig.setMaxEvents(simConfig.maxEvents);
            engineConfig.setTimeStep(simConfig.timeStep);

            SimulationEngine engine = new SimulationEngine(portMap, engineConfig);

            // 7. 注册数据
            for (Entity entity : entities) {
                engine.addEntity(entity);
            }
            for (Instruction task : tasks) {
                engine.addInstruction(task);
            }

            // 8. 运行
            System.out.println("\n=== 开始仿真运行 ===");
            engine.start();

            // 9. 报告
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