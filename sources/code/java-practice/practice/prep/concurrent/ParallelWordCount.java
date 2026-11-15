package practice.prep.concurrent;

import java.nio.file.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public class ParallelWordCount {
    private final int parallism;
    private final Map<String, Long> uniqueWordCounts;
    final AtomicBoolean failed;

    public ParallelWordCount(int parallism) {
        this.parallism = parallism;
        uniqueWordCounts = new ConcurrentHashMap<>();
        failed = new AtomicBoolean(false);
    }

    public Map<String, Long> countUniqueWords(String folderPathStr) {
        Path folderPath = Paths.get(folderPathStr);
        List<List<Path>> fileChunks = new ArrayList<>();
        try {
            int chunkId = 0;
            for (Path file : Files.list(folderPath).toList()) {
                if (fileChunks.size() <= chunkId) {
                    fileChunks.add(new ArrayList<>());
                }

                fileChunks.get(chunkId).add(file);
                chunkId = (chunkId + 1)%parallism;
            }
        } catch (IOException ioException) {
            failed.set(true);
            System.err.println(ioException);
            Thread.currentThread().interrupt();
        }

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < Math.min(parallism, fileChunks.size()); i++) {
            final int threadId = i;
            Thread worker = new Thread(() -> countWords(fileChunks.get(threadId)), "Worker #" + threadId);
            worker.start();
            workers.add(worker);
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                failed.set(true);
                System.err.println("Thread interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        if (failed.get()) {
            System.out.println("Error while computing unique word counts. Returning an empty response, instead of wrong!");
            return Map.of();
        } else {
            return uniqueWordCounts;
        }
    }

    private void countWords(List<Path> files) {
        files.forEach(f -> {
            try {
                Files.lines(f).forEach(line -> {
                    String[] words = line.split("\\W+");
                    for (String word : words) {
                        if (word.isEmpty()) {
                            continue;
                        }

                        uniqueWordCounts.compute(word, (k, v) -> v == null ? 1 : v + 1);
                    }
                });
            } catch (IOException ioException) {
                System.err.println(ioException);
                failed.set(true);
            }
        });
    }
}
