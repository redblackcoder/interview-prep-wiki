package com.practice.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * // This is the HtmlParser's API interface.
 * // You should not implement it, or speculate about its implementation
 * interface HtmlParser {
 * public List<String> getUrls(String url) {}
 * }
 */
import java.util.concurrent.ConcurrentHashMap;

public class WebCrawler {
    public List<String> crawl(String startUrl, HtmlParser htmlParser) {
        LinkedBlockingQueue<String> toCrawl = new LinkedBlockingQueue<>();
        Set<String> seen = ConcurrentHashMap.newKeySet();
        List<String> result = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(4);
        String host = getHost(startUrl);
        toCrawl.add(startUrl);
        seen.add(startUrl);
        result.add(startUrl);

        // phaser initialized to 1 for startUrl work
        Phaser phaser = new Phaser(1);
        List<CompletableFuture<Void>> futures = IntStream.range(0, 4)
                .mapToObj(i -> CompletableFuture.runAsync(() -> work(toCrawl, seen, htmlParser, host, result, phaser),
                        executor))
                .toList();

        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        allOf.join();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException inte) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return result;
    }

    private void work(LinkedBlockingQueue<String> toCrawl, Set<String> seen, HtmlParser hp, String host,
            List<String> result, Phaser phaser) {
        while (true) {
            String next = null;
            try {
                next = toCrawl.poll(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException inte) {
                Thread.currentThread().interrupt();
                return;
            }

            if (next == null) {
                // no work available, check if any pending work left
                if (phaser.getArrivedParties() == 0) {
                    return;
                } else {
                    continue;
                }
            }

            List<String> urls = hp.getUrls(next);
            for (String u : urls) {
                if (!getHost(u).equals(host)) {
                    // ignore
                    continue;
                }

                if (seen.add(u)) {
                    // added to seen, now add to result and queue it up
                    result.add(u);
                    phaser.register();
                    toCrawl.add(u);
                }
            }

            // work for next done
            phaser.arriveAndDeregister();
        }
    }

    private static String getHost(String url) {
        return url.substring(7).split("/", 2)[0];
    }
}
