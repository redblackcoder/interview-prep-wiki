import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KeyedTaskExecutor — concurrency interview problem.
 *
 * Requirements:
 *   1. Thread-safe under overlapping submit() calls.
 *   2. Same key -> tasks run serially, in accepted order.
 *   3. At most 4 tasks execute concurrently (global).
 *   4. At most 1000 queued-or-running tasks globally; reject when full.
 *   5. No task accepted after shutdown begins.
 *   6. submit() reports whether the task was accepted.
 *   7. A failing task must not prevent later tasks from running.
 *   8. shutdown(timeout) stops accepting and drains accepted work up to the timeout.
 *
 * Design: per-key serial queue + shared bounded pool + atomic admission +
 * completion-driven hand-off. See wiki: coding-patterns/keyed-serial-executor.
 */
public final class KeyedTaskExecutor {

    private static final int MAX_CONCURRENCY = 4;
    private static final int MAX_INFLIGHT    = 1000;   // queued + running

    /** One worker thread per permit: the pool IS the <=4 concurrency bound (req 3). */
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_CONCURRENCY);

    /** Pending tasks for one key, plus whether a runner is currently active for it. */
    private static final class KeyQueue {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        boolean scheduled = false;   // a runner is queued/running for this key
        boolean removed   = false;   // torn down; late submitters must re-create
    }

    private final ConcurrentHashMap<Object, KeyQueue> queues = new ConcurrentHashMap<>();
    private final AtomicInteger inflight = new AtomicInteger(0);   // admission counter
    private volatile boolean shuttingDown = false;
    private final Object idle = new Object();                      // signalled at inflight==0

    /** @return true if accepted for execution; false if rejected (full or shutting down). */
    public boolean submit(Object key, Runnable task) {
        // --- Admission: atomic "not shutting down AND not full" via a CAS loop ---
        while (true) {
            if (shuttingDown) return false;                 // req 5
            int n = inflight.get();
            if (n >= MAX_INFLIGHT) return false;            // req 4
            if (inflight.compareAndSet(n, n + 1)) break;    // won a slot
        }

        // --- Enqueue under the key in accepted order; start a runner if none active ---
        while (true) {
            KeyQueue q = queues.computeIfAbsent(key, k -> new KeyQueue()); // atomic get-or-create
            synchronized (q) {
                if (q.removed) continue;                    // racing teardown -> retry with fresh q
                q.tasks.addLast(task);                      // FIFO == accepted order (req 2)
                if (q.scheduled) return true;               // an active runner will pick it up
                q.scheduled = true;                         // we own the (re)start
            }
            pool.execute(() -> drain(key, q));
            return true;
        }
    }

    /** Runs exactly one task for the key, then re-schedules the next (serial per key). */
    private void drain(Object key, KeyQueue q) {
        Runnable task;
        synchronized (q) { task = q.tasks.pollFirst(); }

        try {
            task.run();
        } catch (Throwable t) {
            // Isolate failures so later same-key tasks still run (req 7). Real code: log t.
        } finally {
            if (inflight.decrementAndGet() == 0) {          // free the slot (req 4/8)
                synchronized (idle) { idle.notifyAll(); }
            }
            boolean more;
            synchronized (q) {
                more = !q.tasks.isEmpty();
                if (!more) {                                // key drained: tear it down
                    q.scheduled = false;
                    q.removed   = true;
                    queues.remove(key, q);                  // value-equality removal (cleanup)
                }
            }
            if (more) {
                try {
                    pool.execute(() -> drain(key, q));      // next task for this key, still serial
                } catch (RejectedExecutionException rej) {
                    // Pool hard-stopped after shutdown timeout; drop this key's remaining work.
                }
            }
        }
    }

    /** Stop accepting, drain already-accepted work up to the timeout, then hard-stop. */
    public void shutdown(long timeout, TimeUnit unit) {
        shuttingDown = true;                                // req 5: no new admissions
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        synchronized (idle) {
            long remainingMillis;
            while (inflight.get() > 0
                   && (remainingMillis =
                         TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())) > 0) {
                try {
                    idle.wait(remainingMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        pool.shutdownNow();                                 // interrupt whatever remains (req 8)
    }

    // --- tiny smoke test: same-key ordering + concurrency + failure isolation ---
    public static void main(String[] args) throws Exception {
        KeyedTaskExecutor ex = new KeyedTaskExecutor();
        for (int i = 0; i < 5; i++) {
            final int n = i;
            ex.submit("A", () -> {
                if (n == 2) throw new RuntimeException("boom on A#2"); // req 7: later tasks still run
                System.out.println("A#" + n + " on " + Thread.currentThread().getName());
                sleep(50);
            });
        }
        for (int i = 0; i < 3; i++) {
            final int n = i;
            ex.submit("B", () -> { System.out.println("B#" + n); sleep(50); });
        }
        ex.shutdown(5, TimeUnit.SECONDS);
        System.out.println("done");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
