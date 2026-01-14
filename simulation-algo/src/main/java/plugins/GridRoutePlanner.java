package plugins;

import decision.RoutePlanner;
import map.GridMap;
import map.Location;
import java.util.*;

public class GridRoutePlanner implements RoutePlanner {
    private final GridMap gridMap;
    private static final int[][] DIRS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};

    public GridRoutePlanner(GridMap gridMap) { this.gridMap = gridMap; }

    @Override
    public List<Location> searchRoute(Location start, Location end) {
        if (start == null || end == null || start.equals(end)) return Collections.emptyList();

        // BFS 寻路
        Queue<Location> queue = new LinkedList<>();
        Map<Location, Location> cameFrom = new HashMap<>();

        queue.add(start);
        cameFrom.put(start, null);

        while (!queue.isEmpty()) {
            Location current = queue.poll();
            if (current.equals(end)) break;

            for (int[] dir : DIRS) {
                // Record 访问: .x() .y()
                int nx = current.x() + dir[0];
                int ny = current.y() + dir[1];

                if (gridMap.isWalkable(nx, ny)) {
                    Location next = new Location(nx, ny);
                    if (!cameFrom.containsKey(next)) {
                        queue.add(next);
                        cameFrom.put(next, current);
                    }
                }
            }
        }

        if (!cameFrom.containsKey(end)) return Collections.emptyList();

        LinkedList<Location> path = new LinkedList<>();
        Location curr = end;
        while (curr != null && !curr.equals(start)) {
            path.addFirst(curr);
            curr = cameFrom.get(curr);
        }
        return path;
    }
}