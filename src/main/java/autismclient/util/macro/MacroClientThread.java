package autismclient.util.macro;

import autismclient.AutismClientAddon;
import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class MacroClientThread {
    private MacroClientThread() {
    }
    static void runOnClientThread(Minecraft mc, Runnable runnable) throws InterruptedException {
        if (PackHideState.isHardLocked()) return;
        MacroExecutor.RunState scheduledRun = MacroExecutor.CURRENT_RUN.get();
        Runnable contextual = () -> MacroExecutor.withRunContext(scheduledRun, runnable);
        if (mc.isSameThread()) {
            if (PackHideState.isHardLocked()) return;
            contextual.run();
            return;
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean pending = new AtomicBoolean(true);
        mc.execute(() -> {
            try {
                if (!pending.compareAndSet(true, false)) return;
                if (PackHideState.isHardLocked()) return;
                contextual.run();
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(2000, TimeUnit.MILLISECONDS)) pending.set(false);
    }

    static void runOnClientThread(Minecraft mc, Runnable runnable, boolean tickAligned) throws InterruptedException {
        if (tickAligned) runOnClientTick(mc, runnable);
        else runOnClientThread(mc, runnable);
    }

    public static void postOnClientThread(Minecraft mc, Runnable runnable, boolean tickAligned) throws InterruptedException {
        if (tickAligned) {
            runOnClientTick(mc, runnable);
            return;
        }
        if (PackHideState.isHardLocked()) return;
        MacroExecutor.RunState scheduledRun = MacroExecutor.CURRENT_RUN.get();
        mc.execute(() -> {
            if (PackHideState.isHardLocked()) return;
            MacroExecutor.withRunContext(scheduledRun, runnable);
        });
    }

    static void runOnClientTick(Minecraft mc, Runnable runnable) throws InterruptedException {
        if (PackHideState.isHardLocked()) return;
        MacroExecutor.RunState scheduledRun = MacroExecutor.CURRENT_RUN.get();
        Runnable contextual = () -> MacroExecutor.withRunContext(scheduledRun, runnable);

        if (mc.isSameThread()) {
            if (PackHideState.isHardLocked()) return;
            contextual.run();
            return;
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean pending = new AtomicBoolean(true);
        TICK_ALIGNED.add(() -> {
            try {
                if (!pending.compareAndSet(true, false)) return;
                if (PackHideState.isHardLocked()) return;
                contextual.run();
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(2000, TimeUnit.MILLISECONDS)) pending.set(false);
    }

    static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> TICK_ALIGNED =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static void drainTickAligned() {

        if (autismclient.modules.AutoTotemModule.operationActive()) return;
        Runnable task;
        while ((task = TICK_ALIGNED.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                AutismClientAddon.LOG.debug("[Autism] Tick-aligned macro task failed", t);
            }
        }
    }

    static <T> T callOnClientThread(Minecraft mc, java.util.function.Supplier<T> supplier, T fallback) throws InterruptedException {
        if (PackHideState.isHardLocked()) return fallback;
        MacroExecutor.RunState scheduledRun = MacroExecutor.CURRENT_RUN.get();
        java.util.function.Supplier<T> contextual = () -> {
            AtomicReference<T> value = new AtomicReference<>(fallback);
            MacroExecutor.withRunContext(scheduledRun, () -> value.set(supplier.get()));
            return value.get();
        };
        if (mc.isSameThread()) {
            if (PackHideState.isHardLocked()) return fallback;
            return contextual.get();
        }
        AtomicReference<T> result = new AtomicReference<>(fallback);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean pending = new AtomicBoolean(true);
        mc.execute(() -> {
            try {
                if (!pending.compareAndSet(true, false)) return;
                if (PackHideState.isHardLocked()) return;
                result.set(contextual.get());
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(2000, TimeUnit.MILLISECONDS)) pending.set(false);
        return result.get();
    }

    static <T> T callOnClientThread(Minecraft mc, java.util.function.Supplier<T> supplier, T fallback,
                                            boolean tickAligned) throws InterruptedException {
        return tickAligned ? callOnClientTick(mc, supplier, fallback) : callOnClientThread(mc, supplier, fallback);
    }

    private static <T> T callOnClientTick(Minecraft mc, java.util.function.Supplier<T> supplier, T fallback) throws InterruptedException {
        if (PackHideState.isHardLocked()) return fallback;
        MacroExecutor.RunState scheduledRun = MacroExecutor.CURRENT_RUN.get();
        java.util.function.Supplier<T> contextual = () -> {
            AtomicReference<T> value = new AtomicReference<>(fallback);
            MacroExecutor.withRunContext(scheduledRun, () -> value.set(supplier.get()));
            return value.get();
        };
        if (mc.isSameThread()) {
            if (PackHideState.isHardLocked()) return fallback;
            return contextual.get();
        }
        AtomicReference<T> result = new AtomicReference<>(fallback);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean pending = new AtomicBoolean(true);
        TICK_ALIGNED.add(() -> {
            try {
                if (!pending.compareAndSet(true, false)) return;
                if (PackHideState.isHardLocked()) return;
                result.set(contextual.get());
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(2000, TimeUnit.MILLISECONDS)) pending.set(false);
        return result.get();
    }
}
