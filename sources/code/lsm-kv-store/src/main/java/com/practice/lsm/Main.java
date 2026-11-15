package com.practice.lsm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/** Entry point. Keep logic in small methods so it can be unit-tested. */
public class Main {

    public static void main(String[] args) throws IOException {
        System.out.println("Running single thread test\n");
        singleThread();

        System.out.println("\n\nRunning multi thread test\n");
        multipleThreads();
    }

    private static void singleThread() throws IOException {
        KVStore kvStore = new KVStore();
        kvStore.set("1", "one");
        kvStore.set("2", "two");
        System.out.println(kvStore.get("1"));
        System.out.println(kvStore.get("2"));
        kvStore.delete("1");
        System.out.println(kvStore.get("1"));
        kvStore.set("1", "one-primed");
        System.out.println(kvStore.get("1"));

        kvStore.restore();
        System.out.println(kvStore.get("1"));
        System.out.println(kvStore.get("2"));

        kvStore.close();
    }

    private static void multipleThreads() throws IOException {
        // 4 read threads, 2 write threads, 1 delete thread
        ExecutorService executor = Executors.newFixedThreadPool(7);
        CountDownLatch openGate = new CountDownLatch(1);
        KVStore kvStore = new KVStore();
        List<String> keys = Arrays.asList(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" });

        // 4 read threads
        IntStream.range(0, 4).forEach(i -> executor.execute(() -> waitAndRun(openGate, () -> read(kvStore, keys))));

        // 2 write threads
        IntStream.range(0, 2).forEach(i -> executor.execute(() -> waitAndRun(openGate, () -> write(kvStore, keys))));

        // delete thread
        executor.execute(() -> waitAndRun(openGate, () -> delete(kvStore, keys)));

        // let all run
        openGate.countDown();

        // wait and restore the cache
        bigRandomSleep();

        // run writes again
        IntStream.range(0, 2).forEach(i -> executor.execute(() -> waitAndRun(openGate, () -> write(kvStore, keys))));

        // restore
        kvStore.restore();
        System.out.println("KVStore restored from WAL");

        // read
        read(kvStore, keys);

        // stop tasks and shutdown
        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("Tasks couldn't complete in time, forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException inte) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void waitAndRun(CountDownLatch gate, Callable<?> task) {
        try {
            gate.await(100, TimeUnit.MILLISECONDS);
            task.call();
        } catch (InterruptedException inte) {
            Thread.currentThread().interrupt();
        } catch (Exception exp) {
            System.out.printf("Task %s failed.\n", task.toString());
            exp.printStackTrace();
        }
    }

    private static Void read(KVStore store, List<String> keys) {
        List<String> copy = new ArrayList<>(keys);
        Collections.shuffle(copy);
        for (String k : copy) {
            System.out.printf("[%s] Read %s as %s\n", Thread.currentThread().getName(), k, store.get(k));
            randomSleep();
        }

        return null;
    }

    private static Void write(KVStore store, List<String> keys) throws IOException {
        List<String> copy = new ArrayList<>(keys);
        Collections.shuffle(copy);
        for (String k : copy) {
            store.set(k, k);
            System.out.printf("[%s] Set %s to %s\n", Thread.currentThread().getName(), k, k);
            randomSleep();
        }

        return null;
    }

    private static Void delete(KVStore store, List<String> keys) throws IOException {
        List<String> copy = new ArrayList<>(keys);
        Collections.shuffle(copy);
        for (String k : copy) {
            store.delete(k);
            System.out.printf("[%s] Deleted %s\n", Thread.currentThread().getName(), k);
            randomSleep();
        }

        return null;
    }

    private static void randomSleep() {
        try {
            // Sleep between 0 and 3 milliseconds to stagger execution
            Thread.sleep(ThreadLocalRandom.current().nextInt(4));
        } catch (InterruptedException inte) {
            Thread.currentThread().interrupt();
        }
    }

    private static void bigRandomSleep() {
        try {
            // Sleep between 100 and 300 milliseconds
            Thread.sleep(ThreadLocalRandom.current().nextInt(200) + 100);
        } catch (InterruptedException inte) {
            Thread.currentThread().interrupt();
        }
    }

    static String greet(String name) {
        return "Hello, " + name + "!";
    }
}
