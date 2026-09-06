package autismclient.util.oresim;

import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class AutismOreSimWorker {
    private final ThreadPoolExecutor executor;
    private Work active;

    AutismOreSimWorker(String threadName) {
        executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
                Thread thread = new Thread(task, threadName);
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
    }

    synchronized void submit(Runnable task) {
        if (task == null) throw new IllegalArgumentException("OreSim work must not be null");
        cancelAllLocked();
        Work work = new Work();
        active = work;
        Future<?> future = executor.submit(() -> {
            try {
                task.run();
            } finally {
                completed(work);
            }
        });
        work.future = future;

        if (active != work) future.cancel(true);
    }

    synchronized void cancelAll() {
        cancelAllLocked();
    }

    synchronized boolean isIdle() {
        return active == null && executor.getQueue().isEmpty();
    }

    private void cancelAllLocked() {
        Work work = active;
        active = null;
        executor.getQueue().clear();
        if (work != null && work.future != null) work.future.cancel(true);
    }

    private synchronized void completed(Work work) {
        if (active == work) active = null;
    }

    void shutdownNowForTest() {
        cancelAll();
        executor.shutdownNow();
    }

    private static final class Work {
        volatile Future<?> future;
    }
}
