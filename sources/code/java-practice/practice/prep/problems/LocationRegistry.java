package practice.prep.problems;

import java.util.*;

public class LocationRegistry {
  static class Node implements Comparable<Node> {
    private String segment;
    private Set<Node> children;

    Node(String segment) {
      this.segment = segment;
      this.children = new TreeSet<>();
    }

    @Override
    public int compareTo(Node other) {
      return segment.compareTo(other.segment);
    }
  }

  static class Trie {
    private Set<Node> roots;

    Trie() {
      roots = new TreeSet<>();
    }

    List<String> find(String[] segments) {
      if (roots.size() == 0) {
        return List.of();
      }

      Set<Node> curr = roots;
      List<String> path = new ArrayList<>();
      int i = 0;
      for (; i < segments.length; i++) {
        String s = segments[i];
        Node node = ((TreeSet<Node>)curr).ceiling(new Node(s));
        if (node == null || !node.segment.equals(s)) {
          break;
        } else {
          path.add(node.segment);
        }

        curr = node.children;
      }

      if (i == segments.length) {
        // found ansector
        List<String> allFound = new ArrayList<>();
        appendChildrenPath(curr, path, allFound);
        return allFound;
      }

      if (curr.size() == 0) {
        // found decendent
        return List.of(String.join("/", path));
      }

      return List.of();
    }

    private void appendChildrenPath(Set<Node> children, List<String> soFar, List<String> allFound) {
      if (children.size() == 0) {
        allFound.add(String.join("/", soFar));
        return;
      }

      for (Node n : children) {
        soFar.add(n.segment);
        appendChildrenPath(n.children, soFar, allFound);
        soFar.remove(soFar.size() - 1);
      }
    }

    void add(String[] segments) {
      Set<Node> curr = roots;
      for (int i = 0; i < segments.length; i++) {
        String s = segments[i];
        Node node = ((TreeSet<Node>)curr).ceiling(new Node(s));
        if (node == null || !node.segment.equals(s)) {
          node = new Node(s);
          curr.add(node);
        }

        curr = node.children;
      }
    }

    void remove(String[] segments) {
      Deque<Object[]> toRemove = new LinkedList<>();
      Set<Node> curr = roots;
      boolean missing = false;
      for (int i = 0; i < segments.length; i++) {
        String s = segments[i];
        Node node = ((TreeSet<Node>)curr).ceiling(new Node(s));
        if (node != null && node.segment.equals(s)) {
          toRemove.push(new Object[]{curr, node});
        } else {
          missing = true;
        }

        curr = node.children;
      }

      if (missing) {
        System.out.printf("Cannot find a registered node with location: %s", String.join("/", segments));
        return;
      }

      while (toRemove.size() > 0) {
        Object[] ctx = toRemove.pop();
        Set<Node> set = (Set<Node>)ctx[0];
        Node node = (Node)ctx[1];
        set.remove(node);
      }
    }
  }

  public static class Impl {
    private final Trie storage;
    private final Map<String, String[]> tables;

    Impl() {
      storage = new Trie();
      tables = new HashMap<>();
    }

    List<String> register(String table, String location) {
      // todo: various checks on arguments

      location = location.replace("s3://", "");
      String[] segments = location.split("\\/"); // check this from documentation
      List<String> existing = storage.find(segments);
      if (existing.size() > 0) {
        return existing;
      }

      tables.put(table, segments);
      storage.add(segments);
      return List.of();
    }

    void unregister(String table) {
      String[] segments = tables.get(table);
      storage.remove(segments);
    }
  }
}
