package io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import map.GridMap;
import map.Node;
import map.Segment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JsonMapLoader {
    private final ObjectMapper objectMapper;
    public JsonMapLoader() { this.objectMapper = new ObjectMapper(); }

    public GridMap loadGridMap(String filePath, double cellSize) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) throw new IllegalArgumentException("地图文件不存在: " + filePath);
        JsonNode root = objectMapper.readTree(file);

        List<Node> nodes = parseNodes(root.get("nodes"));
        List<Segment> segments = parseSegments(root.get("segments"));

        double maxX = 0; double maxY = 0;
        for (Node node : nodes) { maxX = Math.max(maxX, node.getX()); maxY = Math.max(maxY, node.getY()); }

        int width = (int) Math.ceil((maxX + 10) / cellSize);
        int height = (int) Math.ceil((maxY + 10) / cellSize);
        GridMap gridMap = new GridMap(width, height, cellSize);

        for (Node node : nodes) {
            int gx = (int) (node.getX() / cellSize); int gy = (int) (node.getY() / cellSize);
            gridMap.registerNodeLocation(node.getId(), gx, gy);
            gridMap.setWalkable(gx, gy, true);
        }

        for (Segment seg : segments) {
            Node from = findNode(nodes, seg.getFromNodeId());
            Node to = findNode(nodes, seg.getToNodeId());
            if (from != null && to != null) rasterizeLine(gridMap, from.getX(), from.getY(), to.getX(), to.getY());
        }
        return gridMap;
    }

    private void rasterizeLine(GridMap map, double x1, double y1, double x2, double y2) {
        double cellSize = map.getCellSize();
        int xStart = (int) (x1 / cellSize); int yStart = (int) (y1 / cellSize);
        int xEnd = (int) (x2 / cellSize); int yEnd = (int) (y2 / cellSize);
        int dx = Math.abs(xEnd - xStart); int dy = Math.abs(yEnd - yStart);
        int sx = xStart < xEnd ? 1 : -1; int sy = yStart < yEnd ? 1 : -1;
        int err = dx - dy;

        while (true) {
            map.setWalkable(xStart, yStart, true);
            if (xStart == xEnd && yStart == yEnd) break;
            int e2 = 2 * err;
            boolean xMoved = false, yMoved = false;
            if (e2 > -dy) { err -= dy; xStart += sx; xMoved = true; }
            if (e2 < dx) { err += dx; yStart += sy; yMoved = true; }

            // 修复：如果发生对角线移动，填充相邻格以保证4-连通性
            if (xMoved && yMoved) map.setWalkable(xStart - sx, yStart, true);
        }
    }

    private Node findNode(List<Node> nodes, String id) { return nodes.stream().filter(n -> n.getId().equals(id)).findFirst().orElse(null); }
    private List<Node> parseNodes(JsonNode nodesArray) {
        List<Node> nodes = new ArrayList<>();
        if (nodesArray != null) {
            for (JsonNode nodeObj : nodesArray) nodes.add(new Node(nodeObj.get("id").asText(), null, nodeObj.get("x").asDouble(), nodeObj.get("y").asDouble()));
        }
        return nodes;
    }
    private List<Segment> parseSegments(JsonNode segmentsArray) {
        List<Segment> segments = new ArrayList<>();
        if (segmentsArray != null) {
            for (JsonNode s : segmentsArray) segments.add(new Segment(s.get("id").asText(), s.get("from").asText(), s.get("to").asText(), 0, 0, false));
        }
        return segments;
    }
}