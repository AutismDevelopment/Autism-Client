package autismclient.util;

import autismclient.AutismClientAddon;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class AutismBackgroundTasks {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    private AutismBackgroundTasks() {
    }

    public static Future<?> runTracked(String name, Runnable work) {
        String label = name == null || name.isBlank() ? "Autism-BG-" + COUNTER.incrementAndGet() : name;
        return POOL.submit(() -> {
            Thread current = Thread.currentThread();
            String original = current.getName();
            current.setName(label);
            try {
                work.run();
            } catch (Throwable t) {
                AutismClientAddon.LOG.error("Background task '" + label + "' failed", t);
            } finally {
                current.setName(original);
            }
        });
    }

    public static <T> CompletableFuture<T> supplyTracked(String name, Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runTracked(name, () -> {
            try {
                future.complete(work.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static Future<?> watchUntil(String name, BooleanSupplier done, Runnable onDone, long pollMs) {
        return runTracked(name, () -> {
            while (!done.getAsBoolean()) {
                try {
                    Thread.sleep(Math.max(1L, pollMs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            onDone.run();
        });
    }
}
