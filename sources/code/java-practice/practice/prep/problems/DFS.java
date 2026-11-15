package practice.prep.problems;

import java.util.*;
import practice.prep.libs.Graph;

public class DFS {
    public static class Result {
        public List<Graph.Node> cycle;
        public List<Node> topoSortedNodes;

        public Result(List<Graph.Node> cycle, List<Node> topoSortedNodes) {
            this.cycle = cycle == null ? null : new ArrayList<>(cycle);
            this.topoSortedNodes = topoSortedNodes;
        }
    }

    private static int time;
    private static List<Graph.Node> cycle;

    public static Result traverse(Graph g) {
        time = 1;
        cycle = null;

        List<Node> topoSortedNodes = new LinkedList<>();
        Map<String, Node> nodes = new HashMap<>();
        g.getVertices().forEach(v -> nodes.put(v.getId(), new Node(v)));
        g.getVertices().forEach(v -> {
            if (nodes.get(v.getId()).color == Color.WHITE) {
                dfs(v, nodes, topoSortedNodes);
            }
        });

        return new Result(cycle, topoSortedNodes);
    }

    private static void dfs(Graph.Node node, Map<String, Node> nodes, List<Node> topoSortedNodes) {
        Node resultNode = nodes.get(node.getId());
        resultNode.color = Color.GREY;
        resultNode.discoverTime = time++;
        node.getAdjacents().forEach(nNode -> {
            Node nResultNode = nodes.get(nNode.getId());
            if (nResultNode.color == Color.WHITE) {
                nResultNode.parent = node;
                dfs(nNode, nodes, topoSortedNodes);
            } else if (nResultNode.color == Color.GREY) {
                // found a cycle
                cycle = new LinkedList<>();
                cycle.add(nNode);
                Graph.Node cNode = node;
                while (cNode != nNode) {
                    cycle.add(0, cNode);
                    cNode = nodes.get(cNode.getId()).parent;
                }
            }
        });

        resultNode.finishTime = time++;
        resultNode.color = Color.BLACK;
        topoSortedNodes.add(0, resultNode);
    }

    public static class Node {
        private Graph.Node graphNode;
        private Graph.Node parent;
        private int discoverTime;
        private int finishTime;
        private Color color;

        public Node(Graph.Node graphNode) {
            this.graphNode = graphNode;
            parent = null;
            discoverTime = Integer.MAX_VALUE;
            finishTime = Integer.MAX_VALUE;
            color = Color.WHITE;
        }

        public Graph.Node getGraphNode() {
            return graphNode;
        }

        public Graph.Node getParent() {
            return parent;
        }

        public int getDiscoverTime() {
            return discoverTime;
        }

        public int getFinishTime() {
            return finishTime;
        }
    }

    private enum Color {
        WHITE,
        GREY,
        BLACK;
    }
}
