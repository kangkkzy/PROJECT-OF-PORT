package app;

import algo.SimpleScheduler;
import core.SimulationEngine;
import decision.RoutePlanner;
import decision.TaskAllocator;
import decision.TaskGenerator;
import decision.TrafficController;
import entity.Entity;
import Instruction.Instruction;
import io.*;
import map.GridMap;
import physics.PhysicsEngine;
import plugins.PhysicsTimeEstimator;
import time.TimeEstimationModule;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            String configPath = args.length > 0 ? args[0] : "config/simulation-config.json";
            var config = new ConfigLoader().load(configPath);

            // 1. 加载基础设施
            GridMap gridMap = new JsonMapLoader().loadGridMap(
                    config.paths().mapFile(),
                    config.mapSettings().cellSize()
            );
            List<Entity> entities = new EntityLoader().loadFromFile(config.paths().entityFile());
            List<Instruction> tasks = new TaskLoader().loadFromFile(config.paths().taskFile());

            // 2. 初始化引擎组件
            PhysicsEngine physics = new PhysicsEngine(gridMap);
            TimeEstimationModule timeModule = new PhysicsTimeEstimator(gridMap);

            // 3. 加载策略插件 (反射)
            // 修改点：使用单参数加载方法
            RoutePlanner routePlanner = loadPlugin(
                    config.strategies().routePlannerClass(),
                    GridMap.class,
                    gridMap
            );

            // 修改点：使用无参加载方法 (消除 null, null 调用)
            Object dispatcher = loadPlugin(config.strategies().taskDispatcherClass());

            // 修改点：使用多参数加载方法 (消除歧义)
            TaskGenerator generator = loadPluginMulti(
                    config.strategies().taskGeneratorClass(),
                    new Class<?>[]{GridMap.class, List.class},
                    new Object[]{gridMap, entities}
            );

            // 4. 组装调度器
            SimpleScheduler scheduler = new SimpleScheduler(
                    (TaskAllocator) dispatcher,
                    (TrafficController) dispatcher,
                    routePlanner,
                    timeModule,
                    physics,
                    gridMap,
                    generator
            );

            // 5. 注入数据
            entities.forEach(scheduler::registerEntity);
            tasks.forEach(scheduler::addInstruction);
            scheduler.init(); // 触发初始状态评估

            // 6. 启动引擎
            SimulationEngine engine = new SimulationEngine(
                    config.timeSettings().endTime(),
                    config.timeSettings().maxEvents(),
                    scheduler
            );

            engine.start();

            // 7. 保存日志
            new LogWriter().writeLog(engine.getEventLog(), config.output().logDir());

        } catch (Exception e) {
            // 修改点：使用更标准的错误输出，或者替换为 logger
            e.printStackTrace();
        }
    }

    // --- 辅助方法重构 ---

    /**
     * 1. 无参构造函数加载器 (用于 Dispatcher)
     */
    @SuppressWarnings("unchecked")
    private static <T> T loadPlugin(String className) throws Exception {
        if (className == null || className.isEmpty()) return null;
        return (T) Class.forName(className).getDeclaredConstructor().newInstance();
    }

    /**
     * 2. 单参数构造函数加载器 (用于 RoutePlanner)
     */
    @SuppressWarnings("unchecked")
    private static <T> T loadPlugin(String className, Class<?> paramType, Object paramValue) throws Exception {
        if (className == null || className.isEmpty()) return null;
        Class<?> clazz = Class.forName(className);
        // 直接使用确定的构造函数，不再进行 Hack 判断
        return (T) clazz.getConstructor(paramType).newInstance(paramValue);
    }

    /**
     * 3. 多参数构造函数加载器 (用于 TaskGenerator)
     * 重命名为 loadPluginMulti 以彻底解决 ambiguous method call 问题
     */
    @SuppressWarnings("unchecked")
    private static <T> T loadPluginMulti(String className, Class<?>[] paramTypes, Object[] args) throws Exception {
        if (className == null || className.isEmpty()) return null;
        Class<?> clazz = Class.forName(className);
        return (T) clazz.getConstructor(paramTypes).newInstance(args);
    }
}