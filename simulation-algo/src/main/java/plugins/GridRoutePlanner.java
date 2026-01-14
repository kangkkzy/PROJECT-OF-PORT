package plugins;

import decision.RoutePlanner;
import map.GridMap;
import map.Location;
import java.util.*;

public class GridRoutePlanner implements RoutePlanner {
    private final GridMap gridMap;
    private static final int[][] DIRS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

    public GridRoutePlanner(GridMap gridMap) {
        this.gridMap = gridMap;
    }

    @Override
    public List<Location> searchRoute(String originId, String destinationId) {
        Location start = gridMap.getNodeLocation(originId);
        Location end = gridMap.getNodeLocation(destinationId);
        return searchRoute(start, end);
    }

    @Override
    public List<Location> searchRoute(Location start, Location end) {
        if (start == null || end == null || start.equals(end)) {
            return Collections.emptyList();
        }
        return aStarSearch(start, end);
    }

    private List<Location> aStarSearch(Location start, Location end) {
        PriorityQueue<NodeRecord> openList = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Location, Double> costSoFar = new HashMap<>();
        Map<Location, Location> cameFrom = new HashMap<>();

        openList.add(new NodeRecord(start, 0, heuristic(start, end)));
        costSoFar.put(start, 0.0);
        cameFrom.put(start, null);

        boolean found = false;

        while (!openList.isEmpty()) {
            NodeRecord current = openList.poll();
            if (current.loc.equals(end)) {
                found = true;
                break;
            }

            for (int[] dir : DIRS) {
                int nextX = current.loc.x + dir[0];
                int nextY = current.loc.y + dir[1];

                if (!gridMap.isWalkable(nextX, nextY)) continue;

                Location nextLoc = new Location(nextX, nextY);
                double newCost = costSoFar.get(current.loc) + 1.0;

                if (!costSoFar.containsKey(nextLoc) || newCost < costSoFar.get(nextLoc)) {
                    costSoFar.put(nextLoc, newCost);
                    double h = heuristic(nextLoc, end);
                    openList.add(new NodeRecord(nextLoc, newCost, newCost + h));
                    cameFrom.put(nextLoc, current.loc);
                }
            }
        }

        if (!found) return Collections.emptyList();

        LinkedList<Location> path = new LinkedList<>();
        Location curr = end;
        while (curr != null && !curr.equals(start)) {
            path.addFirst(curr);
            curr = cameFrom.get(curr);
        }
        return path;
    }

    private double heuristic(Location a, Location b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    private static class NodeRecord {
        Location loc;
        double g; // cost
        double f; // cost + heuristic

        NodeRecord(Location loc, double g, double f) {
            this.loc = loc; this.g = g; this.f = f;
        }
    }
}