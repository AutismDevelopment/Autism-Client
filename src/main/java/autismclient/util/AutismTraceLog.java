package autismclient.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class AutismTraceLog {

    private static final int QUEUE_CAPACITY = 4096;

    private static final BlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private static volatile boolean started;
    private static volatile long dropped;

    private AutismTraceLog() {
    }

    public static void println(String line) {
        if (line == null) return;
        start();
        if (!QUEUE.offer(line)) dropped++;
    }

    private static void start() {
        if (started) return;
        synchronized (AutismTraceLog.class) {
            if (started) return;
            started = true;
            Thread writer = new Thread(AutismTraceLog::drain, "autism-trace-log");
            writer.setDaemon(true);

            writer.setPriority(Thread.MIN_PRIORITY);
            writer.start();
        }
    }

    private static void drain() {
        while (true) {
            try {
                System.out.println(QUEUE.take());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {

            }
        }
    }
}
