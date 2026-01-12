import entity.*;
import Instruction.*;
import map.*;
import org.w3c.dom.Node;

import javax.swing.text.Segment;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("港口调度仿真系统 - 测试程序");

        // 1. 测试实体创建
        System.out.println("\n1. 测试实体创建:");

        QC qc = new QC("QC01", "QUAY_01", 65.0, 12.0);
        System.out.println("   桥吊: " + qc);

        YC yc = new YC("YC01", "BAY_A01", 40.0, 1.2);
        System.out.println("   龙门吊: " + yc);

        IT it = new IT("IT01", "PARK_01", 40.0);
        System.out.println("   集卡: " + it);

        // 2. 测试指令创建
        System.out.println("\n2. 测试指令创建:");

        Instruction instr = new Instruction("INSTR_001", InstructionType.LOAD_TO_SHIP, "BAY_A01", "QUAY_01");
        instr.setContainerId("CONT_001");
        instr.setContainerWeight(25.0);
        instr.setTargetQC("QC01");
        instr.setTargetYC("YC01");
        instr.setTargetIT("IT01");
        System.out.println("   指令: " + instr);

        // 3. 测试地图创建
        System.out.println("\n3. 测试地图创建:");

        PortMap portMap = new PortMap("TEST_MAP");

        // 添加节点
        portMap.addNode(new Node("QUAY_01", NodeType.QUAY, 0, 0, "1号泊位"));
        portMap.addNode(new Node("BAY_A01", NodeType.BAY, 100, 0, "A区1贝"));
        portMap.addNode(new Node("PARK_01", NodeType.PARKING, 50, 50, "1号停车区"));
        portMap.addNode(new Node("ROAD_01", NodeType.ROAD, 25, 25, "1号道路"));

        // 添加路段
        portMap.addSegment(new Segment("SEG_01", "QUAY_01", "ROAD_01", 50.0));
        portMap.addSegment(new Segment("SEG_02", "ROAD_01", "BAY_A01", 50.0));
        portMap.addSegment(new Segment("SEG_03", "ROAD_01", "PARK_01", 35.0));

        System.out.println("   地图: " + portMap);
        System.out.println("   节点数: " + portMap.getNodeCount());
        System.out.println("   路段数: " + portMap.getSegmentCount());

        // 4. 测试路径查找
        System.out.println("\n4. 测试路径查找:");

        List<String> path = portMap.findPath("QUAY_01", "BAY_A01");
        System.out.println("   从QUAY_01到BAY_A01的路径: " + path);

        if (!path.isEmpty()) {
            double distance = portMap.calculatePathDistance(path);
            System.out.println("   路径距离: " + distance + " 米");

            // 估算集卡移动时间
            double moveTime = estimateMovementTime(it, distance);
            System.out.println("   集卡移动时间估算: " + moveTime + " 秒");
        }

        // 5. 测试事件
        System.out.println("\n5. 测试事件:");

        event.SimEvent event1 = new event.SimEvent(
                1000,
                event.EventType.QC_EXECUTION_COMPLETE,
                "QC01",
                "INSTR_001"
        );
        System.out.println("   事件1: " + event1);

        event.SimEvent event2 = new event.SimEvent(
                500,
                event.EventType.IT_ARRIVAL,
                "IT01",
                "INSTR_001",
                "QUAY_01"
        );
        System.out.println("   事件2: " + event2);

        // 6. 测试事件排序
        System.out.println("\n6. 测试事件排序:");

        PriorityQueue<event.SimEvent> eventQueue = new PriorityQueue<>();
        eventQueue.add(event1);
        eventQueue.add(event2);

        System.out.println("   事件队列大小: " + eventQueue.size());
        System.out.println("   第一个事件时间: " + eventQueue.peek().getTimestamp());

        System.out.println("\n测试完成!");
    }

    private static double estimateMovementTime(Entity entity, double distance) {
        double maxSpeed = entity.getMaxSpeed();
        double acceleration = entity.getAcceleration();
        double deceleration = entity.getDeceleration();

        // 简化计算：假设匀速运动
        return distance / maxSpeed;
    }
}