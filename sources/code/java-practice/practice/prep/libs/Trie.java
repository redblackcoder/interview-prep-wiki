package practice.prep.libs;

import java.util.*;

public class Trie {
    private final Map<Character, Node> roots;

    public Trie() {
        roots = new HashMap<>();
    }

    public boolean contains(String word) {
        assert word != null;

        Node node = null;
        for (int i = 0; i < word.length(); i++) {
            if (i == 0) {
                node = roots.get(word.charAt(i));
            } else {
                node = node.getChild(word.charAt(i));
            }

            if (node == null) {
                return false;
            }
        }

        return node == null ? false : node.getWords().contains(word);
    }

    public void addWords(List<String> words) {
        words.forEach(this::addWord);
    }

    public void addWord(String word) {
        assert word != null;

        if (word.length() == 0) {
            return;
        }

        char rootC = word.charAt(0);
        Node leafNode = roots.get(rootC);
        if (leafNode == null) {
            leafNode = new Node(rootC);
            roots.put(rootC, leafNode);
        }

        for (int i = 1; i < word.length(); i++) {
            char nextC = word.charAt(i);
            leafNode = leafNode.addChild(nextC);
        }

        leafNode.addWord(word);
    }

    private static class Node {
        final char c;
        final Map<Character, Node> children;
        final List<String> words;

        public Node(char c) {
            this.c = c;
            children = new HashMap<>();
            words = new ArrayList<>();
        }

        public Node getChild(char c) {
            return children.get(c);
        }

        public Node addChild(char c) {
            Node child = getChild(c);
            if (child == null) {
                child = new Node(c);
                children.put(c, child);
            }

            return child;
        }

        public List<String> getWords() {
            return words;
        }

        public void addWord(String word) {
            words.add(word);
        }
    }
}
