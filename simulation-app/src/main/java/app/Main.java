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
import plugins.PhysicsTimeEstimator; // 或 GridTimeEstimator
import plugins.GridTimeEstimator;
import time.TimeEstimationModule;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            String configPath = args.length > 0 ? args[0] : "config/simulation-config.json";
            // ConfigLoader 现在返回 Record
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
            // 这里使用 GridTimeEstimator 以支持新的 operationTime 接口
            TimeEstimationModule timeModule = new GridTimeEstimator(gridMap);

            // 3. 加载策略插件
            RoutePlanner routePlanner = loadPlugin(
                    config.strategies().routePlannerClass(),
                    GridMap.class,
                    gridMap
            );

            // Dispatcher 同时实现 Allocator 和 TrafficController
            Object dispatcher = loadPlugin(config.strategies().taskDispatcherClass());

            // Generator 需要多参数
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

            // 5. 注入数据并初始化
            entities.forEach(scheduler::registerEntity);
            tasks.forEach(scheduler::addInstruction);
            scheduler.init();

            // 6. 启动
            SimulationEngine engine = new SimulationEngine(
                    config.timeSettings().endTime(),
                    config.timeSettings().maxEvents(),
                    scheduler
            );

            engine.start();

            // 7. 保存日志
            new LogWriter().writeLog(engine.getEventLog(), config.output().logDir());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- 辅助加载器 ---

    @SuppressWarnings("unchecked")
    private static <T> T loadPlugin(String className) throws Exception {
        if (className == null || className.isEmpty()) return null;
        return (T) Class.forName(className).getDeclaredConstructor().newInstance();
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadPlugin(String className, Class<?> paramType, Object paramValue) throws Exception {
        if (className == null || className.isEmpty()) return null;
        return (T) Class.forName(className).getConstructor(paramType).newInstance(paramValue);
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadPluginMulti(String className, Class<?>[] paramTypes, Object[] args) throws Exception {
        if (className == null || className.isEmpty()) return null;
        return (T) Class.forName(className).getConstructor(paramTypes).newInstance(args);
    }
}