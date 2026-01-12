package map;

import java.util.*;

public class PortMap {
    private String mapId;
    private Map<String, Node> nodes;
    private Map<String, Segment> segments;
    private Map<String, List<String>> adjacencyList; // 邻接表

    public PortMap(String mapId) {
        this.mapId = mapId;
        this.nodes = new HashMap<>();
        this.segments = new HashMap<>();
        this.adjacencyList = new HashMap<>();
    }

    public void addNode(Node node) {
        nodes.put(node.getId(), node);
        adjacencyList.put(node.getId(), new ArrayList<>());
    }

    public void addSegment(Segment segment) {
        segments.put(segment.getId(), segment);

        // 更新邻接表
        adjacencyList.computeIfAbsent(segment.getFromNodeId(), k -> new ArrayList<>())
                .add(segment.getToNodeId());

        if (!segment.isOneWay()) {
            adjacencyList.computeIfAbsent(segment.getToNodeId(), k -> new ArrayList<>())
                    .add(segment.getFromNodeId());
        }
    }

    public Node getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public Segment getSegment(String segmentId) {
        return segments.get(segmentId);
    }

    public List<String> getAdjacentNodes(String nodeId) {
        return adjacencyList.getOrDefault(nodeId, Collections.emptyList());
    }

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

    public List<String> findPath(String startNodeId, String endNodeId) {
        // 简化的BFS路径查找
        Map<String, String> previous = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(startNodeId);
        visited.add(startNodeId);
        previous.put(startNodeId, null);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(endNodeId)) {
                // 重建路径
                List<String> path = new ArrayList<>();
                String node = endNodeId;
                while (node != null) {
                    path.add(0, node);
                    node = previous.get(node);
                }
                return path;
            }

            for (String neighbor : getAdjacentNodes(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    previous.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        return Collections.emptyList(); // 没有找到路径
    }

    public double calculatePathDistance(List<String> pathNodes) {
        double totalDistance = 0.0;
        for (int i = 0; i < pathNodes.size() - 1; i++) {
            Segment segment = getSegmentBetween(pathNodes.get(i), pathNodes.get(i + 1));
            if (segment != null) {
                totalDistance += segment.getLength();
            }
        }
        return totalDistance;
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

    public int getNodeCount() {
        return nodes.size();
    }

    public int getSegmentCount() {
        return segments.size();
    }

    @Override
    public String toString() {
        return String.format("地图[%s] 节点数:%d 路段数:%d", mapId, nodes.size(), segments.size());
    }
}