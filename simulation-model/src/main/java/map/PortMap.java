package map;

import java.util.*;

public class PortMap {
    private String mapId;
    private Map<String, Node> nodes;
    private Map<String, Segment> segments;

    public PortMap(String mapId) {
        this.mapId = mapId;
        this.nodes = new HashMap<>();
        this.segments = new HashMap<>();
    }

    public void addNode(Node node) {
        nodes.put(node.getId(), node);
    }

    public void addSegment(Segment segment) {
        segments.put(segment.getId(), segment);
    }

    public Node getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public Segment getSegment(String segmentId) {
        return segments.get(segmentId);
    }

    // 提供基础的几何查询，但不进行路由算法
    public Segment getSegmentBetween(String nodeId1, String nodeId2) {
        for (Segment segment : segments.values()) {
            if (segment.connects(nodeId1, nodeId2)) {
                return segment;
            }
        }
        return null;
    }

    public double getDistance(String fromNodeId, String toNodeId) {
        Segment segment = getSegmentBetween(fromNodeId, toNodeId);
        return segment != null ? segment.getLength() : Double.MAX_VALUE;
    }

    public Collection<Node> getAllNodes() {
        return nodes.values();
    }

    public Collection<Segment> getAllSegments() {
        return segments.values();
    }

    public String getMapId() {
        return mapId;
    }

    @Override
    public String toString() {
        return String.format("地图[%s] 节点数:%d 路段数:%d", mapId, nodes.size(), segments.size());
    }
}