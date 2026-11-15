package practice.prep.concurrent.lrucache;

// A minimal doubly linked list with sentinel head/tail nodes.
// Sentinels remove every null check from addLast/remove.
// NOT thread-safe by itself — the owning cache holds the single lock.
public class DLLSimple {
    static class Node {
        int key, val;            // package-private so the cache can read lru.key on eviction
        private Node prev, next;

        Node(int key, int val) {
            this.key = key;
            this.val = val;
        }
    }

    private final Node head = new Node(0, 0);
    private final Node tail = new Node(0, 0);

    public DLLSimple() {
        head.next = tail;
        tail.prev = head;
    }

    public boolean isEmpty() {
        return head.next == tail;
    }

    public void addLast(Node node) {          // insert just before tail
        node.prev = tail.prev;
        node.next = tail;
        tail.prev.next = node;
        tail.prev = node;
    }

    public void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    public void moveToLast(Node node) {       // names the remove+addLast intent
        remove(node);
        addLast(node);
    }

    public Node removeFirst() {               // returns the real node, not a copy
        if (isEmpty()) return null;
        Node first = head.next;
        remove(first);
        return first;
    }
}
