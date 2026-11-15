package practice.prep.problems;

import java.util.*;

import practice.prep.libs.Graph;

public class TopologySort {

    public static List<String> sort(Map<String, List<String>> adjList) {
        List<String> result = new ArrayList<>();
        Deque<String> zeroOutDegNodes = new ArrayDeque<>();
        Map<String, Integer> outDeg = new HashMap<>();
        Map<String, List<String>> incidentEdges = new HashMap<>();
        adjList.forEach((k, v) -> {
            outDeg.put(k, v.size());
            v.forEach(n -> outDeg.computeIfAbsent(n, key -> 0));
            v.forEach(n -> incidentEdges.computeIfAbsent(n, key -> new ArrayList<>()).add(k));
        });

        outDeg.forEach((k, v) -> {
            if (v == 0) {
                zeroOutDegNodes.addLast(k);
            }
        });

        while (zeroOutDegNodes.size() > 0) {
            String next = zeroOutDegNodes.removeFirst();
            result.add(next);
            incidentEdges.getOrDefault(next, (List<String>)Collections.EMPTY_LIST)
                .forEach(n -> {
                    if (outDeg.compute(n, (k, v) -> v - 1) == 0) {
                        zeroOutDegNodes.addLast(n);
                    }
                });
        }

        return result;
    }

    /**
     * Sort the tasks in a way that all dependencies are satisfied.
     * 
     * E.g.
     * tasks = ["a", "b", "c", "d"]
     * dependencies = [[], ["a"], ["a", "b"], ["a", "b", "c"]]
     * 
     * @param tasks List of tasks
     * @param dependencies List of dependencies, for each task based on index. If a task doesn't ahve any dependency, the list will be empty.
     * @return A list of tasks sorted in a way that all dependencies are satisfied. If there is a cycle, an empty list will be returned.
     */
    public static List<String> sort(List<String> tasks, List<List<String>> dependencies) {
        Graph graph = buildGraph(tasks, dependencies);
        Map<String, Integer> indegree = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            indegree.put(tasks.get(i), dependencies.get(i).size());
        }

        final Deque<String> tasksToExecute = new LinkedList<>();
        indegree.forEach((k, v) -> {
            if (v == 0) {
                tasksToExecute.offerLast(k);
            }
        });

        List<String> sortedTasks = new ArrayList<>(tasks.size());
        while (!tasksToExecute.isEmpty()) {
            String nextTask = tasksToExecute.pollFirst();
            sortedTasks.add(nextTask);

            Graph.Node taskVertex = graph.getVertex(nextTask);
            for (Graph.Node dependent : taskVertex.getAdjacents()) {
                if (indegree.compute(dependent.getId(), (k, v) -> v - 1) == 0) {
                    tasksToExecute.offerLast(dependent.getId());
                }
            }
        }

        if (sortedTasks.size() != tasks.size()) {
            // cycle detected
            return Collections.emptyList();
        }

        return sortedTasks;
    }

    private static Graph buildGraph(List<String> vertices, List<List<String>> edges) {
        Graph graph = new Graph();
        for (String vertex : vertices) {
            graph.addVertex(vertex);
        }

        for (int i = 0; i < vertices.size(); i++) {
            String to = vertices.get(i);
            for (int j = 0; j < edges.get(i).size(); j++) {
                String from = edges.get(i).get(j);
                graph.addEdge(from, to);
            }
        }

        return graph;
    }
}
