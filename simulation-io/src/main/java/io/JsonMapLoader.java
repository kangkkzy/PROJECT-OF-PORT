package io; // 新增

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import map.PortMap;
import map.Node;
import map.NodeType;
import map.Segment;
import java.io.File;
import java.util.*;

/**
 * 纯粹的JSON地图加载器
 * 只负责从JSON文件加载地图配置，没有任何硬编码
 */
public class JsonMapLoader {
    private final ObjectMapper objectMapper;

    public JsonMapLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从JSON文件加载地图（纯解析，无硬编码）
     */
    public PortMap loadFromFile(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("地图文件不存在: " + filePath);
        }

        // 1. 读取JSON文件
        JsonNode root = objectMapper.readTree(file);

        // 2. 解析地图ID
        String mapId = root.get("mapId").asText();

        // 3. 解析节点数组
        List<Node> nodes = parseNodes(root.get("nodes"));

        // 4. 解析路段数组
        List<Segment> segments = parseSegments(root.get("segments"));

        // 5. 创建地图对象
        PortMap portMap = new PortMap(mapId);
        nodes.forEach(portMap::addNode);
        segments.forEach(portMap::addSegment);

        // 6. 验证地图（检查节点引用）
        validateMap(portMap);

        return portMap;
    }

    /**
     * 解析节点数组
     */
    private List<Node> parseNodes(JsonNode nodesArray) {
        List<Node> nodes = new ArrayList<>();

        if (nodesArray != null && nodesArray.isArray()) {
            for (JsonNode nodeObj : nodesArray) {
                String id = nodeObj.get("id").asText();
                String typeStr = nodeObj.get("type").asText();
                double x = nodeObj.get("x").asDouble();
                double y = nodeObj.get("y").asDouble();
                String name = nodeObj.has("name") ? nodeObj.get("name").asText() : id;

                NodeType type = parseNodeType(typeStr);

                nodes.add(new Node(id, type, x, y, name));
            }
        }

        return nodes;
    }

    /**
     * 解析节点类型字符串
     */
    private NodeType parseNodeType(String typeStr) {
        try {
            return NodeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的节点类型: " + typeStr);
        }
    }

    /**
     * 解析路段数组
     */
    private List<Segment> parseSegments(JsonNode segmentsArray) {
        List<Segment> segments = new ArrayList<>();

        if (segmentsArray != null && segmentsArray.isArray()) {
            for (JsonNode segmentObj : segmentsArray) {
                String id = segmentObj.get("id").asText();
                String from = segmentObj.get("from").asText();
                String to = segmentObj.get("to").asText();
                double length = segmentObj.get("length").asDouble();
                double maxSpeed = segmentObj.has("maxSpeed") ? segmentObj.get("maxSpeed").asDouble() : 5.0;
                boolean isOneWay = segmentObj.has("isOneWay") && segmentObj.get("isOneWay").asBoolean(false);

                segments.add(new Segment(id, from, to, length, maxSpeed, isOneWay));
            }
        }

        return segments;
    }

    /**
     * 验证地图完整性
     */
    private void validateMap(PortMap portMap) {
        // 检查所有路段引用的节点是否存在
        for (map.Segment segment : portMap.getAllSegments()) {
            if (portMap.getNode(segment.getFromNodeId()) == null) {
                throw new IllegalArgumentException("路段 " + segment.getId() +
                        " 引用的起点节点不存在: " + segment.getFromNodeId());
            }
            if (portMap.getNode(segment.getToNodeId()) == null) {
                throw new IllegalArgumentException("路段 " + segment.getId() +
                        " 引用的终点节点不存在: " + segment.getToNodeId());
            }
        }
    }
}