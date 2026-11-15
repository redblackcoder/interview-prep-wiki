package practice.prep.libs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Graph {
    private Map<String, Node> vertices;

    public Graph() {
        vertices = new HashMap<>();
    }

    public void addVertex(String id) {
        vertices.computeIfAbsent(id, key -> new Node(key));
    }

    public void addEdge(String fromId, String toId) {
        Node fromNode = vertices.computeIfAbsent(fromId, key -> new Node(key));
        Node toNode = vertices.computeIfAbsent(toId, key -> new Node(key));
        fromNode.addAdjacentNode(toNode);
    }

    public List<Node> getVertices() {
        return new ArrayList<>(vertices.values());
    }

    public Node getVertex(String id) {
        return vertices.get(id);
    }

    public static class Node {
        private String id;
        private Set<Node> adjacent;

        public Node(String id) {
            this.id = id;
            adjacent = new HashSet<>();
        }

        public void addAdjacentNode(Node node) {
            adjacent.add(node);
        }

        public List<Node> getAdjacents() {
            return new ArrayList<>(adjacent);
        }

        public String getId() {
            return id;
        }
    }
}
