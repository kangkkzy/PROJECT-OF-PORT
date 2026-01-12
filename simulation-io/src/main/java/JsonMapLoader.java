import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import map.PortMap;
import map.Node;
import map.NodeType;
import map.Segment;
import java.io.File;
import java.io.InputStream;
import java.util.*;

public class JsonMapLoader {
    private ObjectMapper objectMapper;

    public JsonMapLoader() {
        this.objectMapper = new ObjectMapper();
    }

    public PortMap loadFromFile(String filePath) throws Exception {
        File file = new File(filePath);
        JsonNode root = objectMapper.readTree(file);
        return parseMap(root);
    }

    public PortMap loadFromResource(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            JsonNode root = objectMapper.readTree(is);
            return parseMap(root);
        }
    }

    private PortMap parseMap(JsonNode root) {
        String mapId = root.path("mapId").asText("default_map");
        PortMap portMap = new PortMap(mapId);

        // 解析节点
        JsonNode nodesNode = root.path("nodes");
        if (nodesNode.isArray()) {
            for (JsonNode nodeJson : nodesNode) {
                String id = nodeJson.path("id").asText();
                String typeStr = nodeJson.path("type").asText("ROAD");
                double x = nodeJson.path("x").asDouble(0.0);
                double y = nodeJson.path("y").asDouble(0.0);
                String name = nodeJson.path("name").asText(id);

                NodeType type = NodeType.valueOf(typeStr);
                Node node = new Node(id, type, x, y, name);
                portMap.addNode(node);
            }
        }

        // 解析路段
        JsonNode segmentsNode = root.path("segments");
        if (segmentsNode.isArray()) {
            for (JsonNode segmentJson : segmentsNode) {
                String id = segmentJson.path("id").asText();
                String fromNodeId = segmentJson.path("from").asText();
                String toNodeId = segmentJson.path("to").asText();
                double length = segmentJson.path("length").asDouble(100.0);
                double maxSpeed = segmentJson.path("maxSpeed").asDouble(5.0);
                boolean isOneWay = segmentJson.path("isOneWay").asBoolean(false);

                Segment segment = new Segment(id, fromNodeId, toNodeId, length, maxSpeed, isOneWay);
                portMap.addSegment(segment);
            }
        }

        return portMap;
    }

    public void saveToFile(PortMap portMap, String filePath) throws Exception {
        Map<String, Object> mapData = new HashMap<>();
        mapData.put("mapId", portMap.getMapId());

        // 节点数据
        List<Map<String, Object>> nodesData = new ArrayList<>();
        for (map.Node node : portMap.getAllNodes()) {
            Map<String, Object> nodeData = new HashMap<>();
            nodeData.put("id", node.getId());
            nodeData.put("type", node.getType().name());
            nodeData.put("x", node.getX());
            nodeData.put("y", node.getY());
            nodeData.put("name", node.getName());
            nodesData.add(nodeData);
        }
        mapData.put("nodes", nodesData);

        // 路段数据
        List<Map<String, Object>> segmentsData = new ArrayList<>();
        for (map.Segment segment : portMap.getAllSegments()) {
            Map<String, Object> segmentData = new HashMap<>();
            segmentData.put("id", segment.getId());
            segmentData.put("from", segment.getFromNodeId());
            segmentData.put("to", segment.getToNodeId());
            segmentData.put("length", segment.getLength());
            segmentData.put("maxSpeed", segment.getMaxSpeed());
            segmentData.put("isOneWay", segment.isOneWay());
            segmentsData.add(segmentData);
        }
        mapData.put("segments", segmentsData);

        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(filePath), mapData);
    }

    public boolean validateMapFile(String filePath) {
        try {
            PortMap map = loadFromFile(filePath);
            System.out.println("地图验证成功: " + map);
            return true;
        } catch (Exception e) {
            System.err.println("地图验证失败: " + e.getMessage());
            return false;
        }
    }
}