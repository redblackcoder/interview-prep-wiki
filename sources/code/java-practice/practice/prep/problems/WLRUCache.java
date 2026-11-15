package practice.prep.problems;

import java.util.HashMap;
import java.util.Map;

// A weighted version of LRU cache.
// Each item take up nummber of slots in the cache equal to their weight.
public class WLRUCache {
    static class Node {
        String key;
        String val;
        int wt;
        Node next;
        Node prev;

        Node(String key, String val, int wt) {
            this.key = key;
            this.val = val;
            this.wt = wt;
        }
    }

    Node head = new Node(null, null, 0);
    Node tail = new Node(null, null, 0);
    Map<String, Node> index = new HashMap<>();
    int cap;
    int size;

    public WLRUCache(int cap) {
        this.cap = cap;
        size = 0;
        head.next = tail;
        tail.prev = head;
    }

    // cap = 10
    // size = 5 (k1: 1, k2: 4)
    // put k2, two, 10
    // wtDelta = 6
    void put(String key, String val, int wt) {
        if (wt <= 0 || wt > cap) {
            throw new IllegalArgumentException();
        }

        Node node = index.get(key);
        int wtDelta = node == null ? wt : wt - node.wt;
        if (node != null && wtDelta <= (cap - size)) {
            size += wtDelta;
            node.val = val;
            node.wt = wt;
            remove(node);
            add(node);
            return;
        }

        if (node == null) {
            node = new Node(key, val, wt);
        } else {
            node.val = val;
            node.wt = wt;
            remove(node);
        }

        add(node);
        index.put(key, node);
        size += wtDelta;

        while (size > cap) {
            Node removed = head.next;
            remove(removed);
            size -= removed.wt;
            index.remove(removed.key);
        }
    }

    String get(String key) {
        Node node = index.get(key);
        if (node == null) {
            return null;
        }

        remove(node);
        add(node);
        return node.val;
    }

    void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    void add(Node node) {
        node.prev = tail.prev;
        tail.prev.next = node;
        node.next = tail;
        tail.prev = node;
    }
}
