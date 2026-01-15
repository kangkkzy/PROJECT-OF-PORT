package app;

import algo.SimpleScheduler;
import core.SimulationEngine;
import decision.*;
import entity.Entity;
import Instruction.Instruction;
import io.*;
import map.GridMap;
import physics.PhysicsEngine;
import plugins.GridTimeEstimator;
import time.TimeEstimationModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            var loader = new ConfigLoader();
            // 兼容命令行参数和默认配置路径
            String configPath = args.length > 0 ? args[0] : "config/simulation-config.json";
            var config = loader.load(configPath);

            // 1. 加载静态资源
            GridMap gridMap = new JsonMapLoader().loadGridMap(config.paths().mapFile(), config.mapSettings().cellSize());
            List<Entity> entities = new EntityLoader().loadFromFile(config.paths().entityFile());
            List<Instruction> tasks = new TaskLoader().loadFromFile(config.paths().taskFile());

            // 2. 初始化基础模块
            PhysicsEngine physics = new PhysicsEngine(gridMap);
            TimeEstimationModule timeModule = new GridTimeEstimator(gridMap);

            // 3. 动态加载策略插件
            RoutePlanner routePlanner = loadPlugin(config.strategies().routePlannerClass(), GridMap.class, gridMap);

            Object dispatcherObj = loadPlugin(config.strategies().taskDispatcherClass());
            TaskAllocator taskAllocator = (TaskAllocator) dispatcherObj;
            TrafficController trafficController = (TrafficController) dispatcherObj;

            // Generator 需要 Map 和 Entity 列表
            TaskGenerator generator = loadPluginMulti(config.strategies().taskGeneratorClass(),
                    new Class<?>[]{GridMap.class, List.class}, new Object[]{gridMap, entities});

            SimulationValidator validator = loadPlugin(config.strategies().validatorClass());
            MetricsAnalyzer analyzer = loadPlugin(config.strategies().analyzerClass());

            // 4. 构建调度器
            SimpleScheduler scheduler = new SimpleScheduler(
                    taskAllocator, trafficController,
                    routePlanner, timeModule, physics, gridMap, generator
            );

            // 注册初始数据到调度器
            entities.forEach(scheduler::registerEntity);
            tasks.forEach(scheduler::addInstruction);

            // 初始化调度器 (位置锁定、初始任务分配)
            scheduler.init();

            // 5. 启动引擎
            // 修正：使用 var 自动推断类型，防止 config 返回 int 而我们强转 long 导致构造函数不匹配
            // Math.max 确保至少有 100000 个事件配额，防止仿真过早退出
            var maxEvents = Math.max(config.timeSettings().maxEvents(), 100000);

            SimulationEngine engine = new SimulationEngine(config.timeSettings().endTime(), maxEvents, scheduler);
            engine.setValidator(validator);
            engine.setAnalyzer(analyzer);

            logger.info("仿真引擎启动...");
            engine.start();

            // 6. 输出日志
            new LogWriter().writeLog(engine.getEventLog(), config.output().logDir());
            logger.info("仿真结束，日志已保存至: " + config.output().logDir());

        } catch (Exception e) {
            logger.error("仿真运行失败", e);
            e.printStackTrace();
        }
    }

    // --- 反射工具方法 ---

    private static <T> T loadPlugin(String className) throws Exception {
        if (className == null || className.isEmpty()) return null;
        return (T) Class.forName(className).getDeclaredConstructor().newInstance();
    }

    private static <T> T loadPlugin(String className, Class<?> paramType, Object paramValue) throws Exception {
        if (className == null || className.isEmpty()) return null;
        try {
            return (T) Class.forName(className).getConstructor(paramType).newInstance(paramValue);
        } catch (NoSuchMethodException e) {
            // 降级：尝试无参构造
            return (T) Class.forName(className).getDeclaredConstructor().newInstance();
        }
    }

    private static <T> T loadPluginMulti(String className, Class<?>[] paramTypes, Object[] args) throws Exception {
        if (className == null || className.isEmpty()) return null;
        return (T) Class.forName(className).getConstructor(paramTypes).newInstance(args);
    }
}