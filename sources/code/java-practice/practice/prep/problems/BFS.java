package practice.prep.problems;

import java.util.*;
import practice.prep.libs.Graph;

public class BFS {
    public static TreeNode traverse(Graph graph, Graph.Node source) {
        Map<String, InternalNode> nodes = new HashMap<>();
        graph.getVertices().forEach(v -> nodes.put(v.getId(), new InternalNode(v, Integer.MAX_VALUE)));

        Deque<InternalNode> queue = new LinkedList<>();
        TreeNode root = new TreeNode(source.getId(), 0);
        InternalNode sourceNode = nodes.get(source.getId()).withColor(Color.GREY).withDistance(0).withTreeNode(root);
        queue.offerLast(sourceNode);
        while (!queue.isEmpty()) {
            InternalNode nextNode = queue.pollFirst();
            for (Graph.Node neighbor: nextNode.node.getAdjacents()) {
                InternalNode neighborAsInternal = nodes.get(neighbor.getId());
                if (neighborAsInternal.color == Color.WHITE) {
                    TreeNode child = new TreeNode(neighbor.getId(), nextNode.distance + 1);
                    nextNode.treeNode.addChild(child);
                    queue.offerLast(neighborAsInternal.withColor(Color.GREY).withDistance(nextNode.distance + 1).withTreeNode(child));
                }
            }

            nextNode.withColor(Color.BLACK);
        }

        return root;
    }

    public static class TreeNode {
        private String label;
        private int distance;
        private List<TreeNode> children;

        public TreeNode(String label, int distance) {
            this.label = label;
            this.distance = distance;
            children = new ArrayList<>();
        }

        public void addChild(TreeNode child) {
            children.add(child);
        }

        public String getLabel() {
            return label;
        }

        public List<TreeNode> getChildren() {
            return new ArrayList<>(children);
        }

        public int getDistance() {
            return distance;
        }
    }

    private enum Color {
        WHITE,
        GREY,
        BLACK;
    }

    private static class InternalNode {
        Graph.Node node;
        int distance;
        Color color;
        TreeNode treeNode;

        InternalNode(Graph.Node node, int distance) {
            this.node = node;
            this.distance = distance;
            color = Color.WHITE;
        }

        InternalNode withColor(Color color) {
            this.color = color;
            return this;
        }

        InternalNode withDistance(int distance) {
            this.distance = distance;
            return this;
        }

        InternalNode withTreeNode(TreeNode treeNode) {
            this.treeNode = treeNode;
            return this;
        }
    }
}
