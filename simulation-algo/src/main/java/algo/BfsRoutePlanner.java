package algo;

import decision.RoutePlanner;
import map.PortMap;
import map.Segment;
import java.util.*;

/**
 * BFS 路径规划器实现
 */
public class BfsRoutePlanner implements RoutePlanner {
    private final PortMap portMap;

    public BfsRoutePlanner(PortMap portMap) {
        this.portMap = portMap;
    }

    @Override
    public List<String> searchRoute(String originId, String destinationId) {
        if (originId.equals(destinationId)) return Collections.emptyList();

        Map<String, List<String>> adjGraph = buildAdjacencyGraph();

        Queue<String> queue = new LinkedList<>();
        Map<String, String> previous = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(originId);
        visited.add(originId);
        previous.put(originId, null);

        boolean found = false;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(destinationId)) {
                found = true;
                break;
            }
            for (String neighbor : adjGraph.getOrDefault(current, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    previous.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        if (!found) return Collections.emptyList();

        // 回溯重建路径
        List<String> routeSegmentIds = new ArrayList<>();
        String curr = destinationId;
        while (curr != null) {
            String prev = previous.get(curr);
            if (prev != null) {
                Segment seg = portMap.getSegmentBetween(prev, curr);
                if (seg != null) routeSegmentIds.add(0, seg.getId());
            }
            curr = prev;
        }
        return routeSegmentIds;
    }

    private Map<String, List<String>> buildAdjacencyGraph() {
        Map<String, List<String>> graph = new HashMap<>();
        for (Segment seg : portMap.getAllSegments()) {
            graph.computeIfAbsent(seg.getFromNodeId(), k -> new ArrayList<>()).add(seg.getToNodeId());
            if (!seg.isOneWay()) {
                graph.computeIfAbsent(seg.getToNodeId(), k -> new ArrayList<>()).add(seg.getFromNodeId());
            }
        }
        return graph;
    }
}