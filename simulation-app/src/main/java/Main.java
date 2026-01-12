import entity.*;
import Instruction.*;
import map.PortMap;
import map.Node;
import JsonMapLoader;
import EntityLoader;
import TaskLoader;
import ConfigLoader;
import ConfigLoader.SimulationConfig;
import SimulationEngine;
import java.util.*;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. 加载配置文件
            String configFile = getConfigFilePath(args);
            ConfigLoader configLoader = new ConfigLoader();
            SimulationConfig simConfig = configLoader.loadConfig(configFile);

            System.out.println("加载配置文件: " + configFile);

            // 2. 加载地图（从JSON文件）
            System.out.println("加载地图文件: " + simConfig.mapFile);
            JsonMapLoader mapLoader = new JsonMapLoader();
            PortMap portMap = mapLoader.loadFromFile(simConfig.mapFile);
            System.out.println("地图加载成功: " + portMap.getMapId());

            // 创建节点映射表，用于任务加载
            Map<String, Node> nodeMap = createNodeMap(portMap);

            // 3. 加载设备实体（从JSON文件）
            System.out.println("加载实体文件: " + simConfig.entityFile);
            EntityLoader entityLoader = new EntityLoader();
            List<Entity> entities = entityLoader.loadFromFile(simConfig.entityFile);

            // 4. 加载任务指令（从JSON文件）
            System.out.println("加载任务文件: " + simConfig.taskFile);
            TaskLoader taskLoader = new TaskLoader();
            List<Instruction> tasks = taskLoader.loadFromFile(simConfig.taskFile, nodeMap);

            // 5. 创建仿真引擎
            SimulationEngine.SimulationConfig engineConfig = new SimulationEngine.SimulationConfig();
            engineConfig.setSimulationDuration(simConfig.endTime);
            engineConfig.setMaxEvents(simConfig.maxEvents);

            SimulationEngine engine = new SimulationEngine(portMap, engineConfig);

            // 6. 注册所有实体
            for (Entity entity : entities) {
                engine.addEntity(entity);
            }
            System.out.println("注册设备: " + entities.size() + " 个");

            // 7. 添加所有任务
            for (Instruction task : tasks) {
                engine.addInstruction(task);
            }
            System.out.println("添加任务: " + tasks.size() + " 个");

            // 8. 运行仿真
            System.out.println("\n开始仿真...");
            engine.start();

            // 9. 生成报告
            engine.generateReport();

        } catch (Exception e) {
            System.err.println("仿真错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 获取配置文件路径
     */
    private static String getConfigFilePath(String[] args) {
        if (args.length > 0) {
            return args[0];
        }
        return "config/simulation-config.json";
    }

    /**
     * 创建节点映射表
     */
    private static Map<String, Node> createNodeMap(PortMap portMap) {
        Map<String, Node> nodeMap = new HashMap<>();
        for (map.Node node : portMap.getAllNodes()) {
            nodeMap.put(node.getId(), node);
        }
        return nodeMap;
    }
}