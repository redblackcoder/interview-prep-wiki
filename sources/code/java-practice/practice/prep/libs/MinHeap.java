package practice.prep.libs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A min-healp implementation which supports adding values which are uniquely identified by corresponding keys.
 * The set of keys are unique, so no duplicate keys can exist in the heap. If presented with multiple values for
 * the same key, they will overwrite and last one wins.
 * 
 * This is not thread-safe!
 * 
 * @param <K> Key Type.
 * @param <V> Value Type. It should be comparable and a comparator should be provided during construction if natural
 *    ordering is not sufficient.
 */
public class MinHeap<K, V extends Comparable<? super V>> {
    public class Node {
        private K key;
        private V value;

        public Node(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public Node(Node other) {
            key = other.key;
            value = other.value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
    }

    private final int capacity;
    private final Node[] elements;
    private final Map<K, Integer> index;
    private final Comparator<V> comparator;
    private final Adder<V> adder;
    private int size;

    public MinHeap(int capacity, Class<V> clazz) {
        this(capacity, Comparator.<V>naturalOrder(), Adder.DEFAULT(clazz));
    }

    public MinHeap(int capacity, Comparator<V> comparator, Adder<V> adder) {
        this.capacity = capacity;
        elements = (Node[])new MinHeap.Node[capacity];
        index = new HashMap<>();
        this.comparator = comparator;
        this.adder = adder;
        size = 0;
    }

    public List<Node> getElements() {
        List<Node> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(new Node(elements[i]));
        }

        return result;
    }

    public void add(K key, V value) {
        Integer foundIdx = index.get(key);
        if (foundIdx != null) {
            updateInternal(foundIdx, value);
            return;
        }

        if (size == capacity) {
            throw new RuntimeException("Heap is full!");
        }

        addInternal(key, value);
    }

    public Node getMin() {
        if (size == 0) {
            throw new RuntimeException("Heap is empty!");
        }

        return new Node(elements[0].key, elements[0].value);
    }

    public Node removeMin() {
        Node result = getMin();
        swap(0, --size);
        index.remove(elements[size].key);
        heapify(0);
        return result;
    }

    public void increaseValue(K key, V increaseBy) {
        Integer foundIdx = index.get(key);
        if (foundIdx == null) {
            throw new RuntimeException("Key: " + key + " not found in the heap!");
        }

        updateInternal(foundIdx, adder.add(elements[foundIdx].value, increaseBy));
    }

    public int size() {
        return size;
    }

    private void updateInternal(int idx, V value) {
        elements[idx].value = value;
        heapifyToRoot(idx);
        heapify(idx);
    }

    private void addInternal(K key, V value) {
        Node node = new Node(key, value);
        elements[size++] = node;
        index.put(key, size - 1);
        heapifyToRoot(size - 1);
    }

    private void heapifyToRoot(int idx) {
        int pIdx = (idx - 1)/2;
        while (pIdx >= 0 && elements[idx].value.compareTo(elements[pIdx].value) < 0) {
            swap(idx, pIdx);
            idx = pIdx;
            pIdx = (idx - 1)/2;
        }
    }

    private void heapify(int idx) {
        int leftChildIdx = 2*idx + 1;
        int rightChildIdx = 2*idx + 2;
        if (leftChildIdx >= size) {
            return;
        }

        int minChildIdx = leftChildIdx;
        if (rightChildIdx < size && elements[rightChildIdx].value.compareTo(elements[leftChildIdx].value) < 0) {
            minChildIdx = rightChildIdx;
        }

        if (elements[minChildIdx].value.compareTo(elements[idx].value) >= 0) {
            return;
        }

        swap(idx, minChildIdx);
        heapify(minChildIdx);
    }

    private void swap(int i, int j) {
        Node saveI = elements[i];
        elements[i] = elements[j];
        elements[j] = saveI;

        index.put(elements[i].key, i);
        index.put(elements[j].key, j);
    }
}
