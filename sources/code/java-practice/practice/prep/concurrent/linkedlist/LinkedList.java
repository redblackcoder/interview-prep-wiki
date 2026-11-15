package practice.prep.concurrent.linkedlist;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LinkedList {
    static class Node {
        final Lock lock = new ReentrantLock();
        final String val;
        volatile Node next;

        Node(String val, Node next) {
            this.val = val;
            this.next = next;
        }
    }

    // head -> 0 -> 1 -> 3
    // insert("2", 2)
    // get(2)
    // delete(2)
    private final Node head = new Node(null, null);

    public String get(int index) {
        Node prev = beforeLocked(index);
        try {
            Node next = prev.next;
            if (next == null) {
                throw new IndexOutOfBoundsException(index);
            }

            return next.val;
        } finally {
            prev.lock.unlock();
        }
    }

    public void insert(String val, int index) {
        Node prev = beforeLocked(index);
        try {
            prev.next = new Node(val, prev.next);
        } finally {
            prev.lock.unlock();
        }
    }

    public void delete(int index) {
        Node prev = beforeLocked(index);
        try {
            Node next = prev.next;
            if (next == null) {
                throw new IndexOutOfBoundsException(index);
            }

            next.lock.lock();
            try {
                prev.next = next.next;
            } finally {
                next.lock.unlock();
            }
        } finally {
            prev.lock.unlock();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Node curr = head;
        curr.lock.lock();
        try {
            while (curr.next != null) {
                sb.append(String.format("%s -> ", curr.next.val));
                Node next = curr.next;
                next.lock.lock();
                curr.lock.unlock();
                curr = next;
            }
        } finally {
            curr.lock.unlock();
        }

        return sb.toString();
    }

    private Node beforeLocked(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(index);
        }

        Node curr = head;
        curr.lock.lock();
        for (int i = 0; i < index; i++) {
            Node next = curr.next;
            if (next == null) {
                curr.lock.unlock();
                throw new IndexOutOfBoundsException(index);
            }

            next.lock.lock();
            curr.lock.unlock();
            curr = next;
        }

        return curr;
    }
}
