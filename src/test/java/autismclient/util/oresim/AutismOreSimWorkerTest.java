package autismclient.util.oresim;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismOreSimWorkerTest {

    @Test
    void cancelInterruptsWorkAlreadyRunning() throws Exception {
        AutismOreSimWorker worker = new AutismOreSimWorker("oresim-cancel-test");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            worker.submit(() -> {
                started.countDown();
                try {
                    while (true) Thread.sleep(10_000L);
                } catch (InterruptedException expected) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(started.await(2, TimeUnit.SECONDS), "worker task never started");
            worker.cancelAll();
            assertTrue(interrupted.await(2, TimeUnit.SECONDS),
                "cancel must interrupt a scan that is already running");
            assertTrue(worker.isIdle(), "cancel must leave no active or queued work");
        } finally {
            worker.shutdownNowForTest();
        }
    }
}
