package io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import map.GridMap;
import map.Location;
import java.io.File;

public class JsonMapLoader {
    public GridMap loadGridMap(String filePath, double cellSize) throws Exception {
        JsonNode root = new ObjectMapper().readTree(new File(filePath));

        double maxX = 0, maxY = 0;
        for (JsonNode n : root.path("nodes")) {
            maxX = Math.max(maxX, n.path("x").asDouble());
            maxY = Math.max(maxY, n.path("y").asDouble());
        }

        GridMap gridMap = new GridMap((int)Math.ceil(maxX/cellSize) + 5, (int)Math.ceil(maxY/cellSize) + 5, cellSize);

        for (JsonNode n : root.path("nodes")) {
            String id = n.path("id").asText();
            String type = n.has("type") ? n.path("type").asText() : "UNKNOWN";
            int gx = (int)(n.path("x").asDouble() / cellSize);
            int gy = (int)(n.path("y").asDouble() / cellSize);

            Location loc = new Location(gx, gy);
            gridMap.registerNode(id, type, loc);
            gridMap.setWalkable(gx, gy, true);
        }

        for (JsonNode s : root.path("segments")) {
            Location from = gridMap.getNodeLocation(s.path("from").asText());
            Location to = gridMap.getNodeLocation(s.path("to").asText());
            if (from != null && to != null) {
                rasterizeLine(gridMap, from, to);
            }
        }
        return gridMap;
    }

    private void rasterizeLine(GridMap map, Location p1, Location p2) {
        int x0 = p1.x(), y0 = p1.y();
        int x1 = p2.x(), y1 = p2.y();
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            map.setWalkable(x0, y0, true);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }
}