import entity.*;
import Instruction.*;
import map.PortMap;
import JsonMapLoader;
import SimulationEngine;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== 港口调度仿真系统 ===");
        System.out.println("版本: 最小Demo版");
        System.out.println("====================\n");

        try {
            // 1. 加载地图
            System.out.println("1. 加载地图...");
            JsonMapLoader mapLoader = new JsonMapLoader();
            PortMap portMap = mapLoader.loadFromFile("config/map.json");
            System.out.println("   地图加载成功: " + portMap);

            // 2. 创建仿真引擎
            System.out.println("2. 初始化仿真引擎...");
            SimulationEngine.SimulationConfig config = new SimulationEngine.SimulationConfig();
            config.setSimulationDuration(3600000); // 1小时
            config.setMaxEvents(10000);

            SimulationEngine engine = new SimulationEngine(portMap, config);

            // 3. 创建设备
            System.out.println("3. 创建设备...");

            // 桥吊
            QC qc1 = new QC("QC01", "QUAY_01", 65.0, 12.0);
            QC qc2 = new QC("QC02", "QUAY_02", 65.0, 12.0);
            engine.addEntity(qc1);
            engine.addEntity(qc2);

            // 龙门吊
            YC yc1 = new YC("YC01", "BAY_A01", 40.0, 1.2);
            YC yc2 = new YC("YC02", "BAY_B01", 40.0, 1.2);
            engine.addEntity(yc1);
            engine.addEntity(yc2);

            // 集卡
            IT it1 = new IT("IT01", "PARK_01", 40.0);
            IT it2 = new IT("IT02", "PARK_02", 40.0);
            IT it3 = new IT("IT03", "PARK_03", 40.0);
            engine.addEntity(it1);
            engine.addEntity(it2);
            engine.addEntity(it3);

            System.out.println("   创建了 2台桥吊, 2台龙门吊, 3台集卡");

            // 4. 创建指令
            System.out.println("4. 创建指令...");

            // 装船指令
            Instruction instr1 = new Instruction("INSTR_001", InstructionType.LOAD_TO_SHIP, "BAY_A01", "QUAY_01");
            instr1.setContainerId("CONT_001");
            instr1.setContainerWeight(25.0);
            instr1.setTargetQC("QC01");
            instr1.setTargetYC("YC01");
            instr1.setTargetIT("IT01");
            instr1.setPriority(1);
            engine.addInstruction(instr1);

            Instruction instr2 = new Instruction("INSTR_002", InstructionType.LOAD_TO_SHIP, "BAY_B01", "QUAY_02");
            instr2.setContainerId("CONT_002");
            instr2.setContainerWeight(28.0);
            instr2.setTargetQC("QC02");
            instr2.setTargetYC("YC02");
            instr2.setTargetIT("IT02");
            instr2.setPriority(1);
            engine.addInstruction(instr2);

            // 卸船指令
            Instruction instr3 = new Instruction("INSTR_003", InstructionType.UNLOAD_FROM_SHIP, "QUAY_01", "BAY_A02");
            instr3.setContainerId("CONT_003");
            instr3.setContainerWeight(30.0);
            instr3.setTargetQC("QC01");
            instr3.setTargetYC("YC01");
            instr3.setTargetIT("IT03");
            instr3.setPriority(2);
            engine.addInstruction(instr3);

            System.out.println("   创建了 3条指令");

            // 5. 添加初始事件
            System.out.println("5. 设置初始事件...");

            // 假设集卡IT01已经到达堆场BAY_A01，等待龙门吊YC01
            it1.setCurrentPosition("BAY_A01");
            it1.setStatus(EntityStatus.WAITING);

            // 添加龙门吊执行完成事件（模拟龙门吊已经完成上一个操作）
            event.SimEvent initialEvent = new event.SimEvent(
                    0,
                    event.EventType.YC_EXECUTION_COMPLETE,
                    "YC01",
                    null
            );
            engine.addEvent(initialEvent);

            System.out.println("   添加了 1个初始事件");

            // 6. 运行仿真
            System.out.println("\n6. 开始仿真...");
            System.out.println("====================");
            engine.start();

            // 7. 生成报告
            System.out.println("\n====================");
            engine.generateReport();

        } catch (Exception e) {
            System.err.println("仿真运行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}