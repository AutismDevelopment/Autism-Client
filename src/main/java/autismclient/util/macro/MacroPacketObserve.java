package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismPacketNamer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.concurrent.CompletableFuture;

final class MacroPacketObserve {
    private MacroPacketObserve() {
    }
    static final class TimedOneShotPacketListener {
        public final java.util.concurrent.CompletableFuture<MacroExecutor.MacroEvent> future = new java.util.concurrent.CompletableFuture<>();
        private final java.util.List<TimedOneShotPacketListener> registry;
        private final TimedPacketMatch match;
        private final long expireAtNanos;
        TimedOneShotPacketListener(java.util.List<TimedOneShotPacketListener> registry, TimedPacketMatch match) {
            this.registry = registry;
            this.match = match;
            this.expireAtNanos = System.nanoTime() + MacroExecutor.ONE_SHOT_EXPIRE_NANOS;
            registry.add(this);
        }

        boolean offer(MacroExecutor.MacroEvent event) {
            if (future.isDone()) return true;
            if (event.nanoTime() - expireAtNanos >= 0L) { future.cancel(true); return true; }
            if (match.test(event.packet(), event.direction(), event.nanoTime())) {
                future.complete(event);
                return true;
            }
            return false;
        }
        public void cancel() {
            registry.remove(this);
            if (!future.isDone()) future.cancel(true);
        }
    }

    interface TimedPacketMatch {
        boolean test(Packet<?> packet, String direction, long nanoTime);
    }

    static final java.util.List<MacroExecutor.OneShotPacketListener> ONE_SHOT_RECV =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    static final java.util.List<MacroExecutor.OneShotPacketListener> ONE_SHOT_SEND =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    static final java.util.List<TimedOneShotPacketListener> TIMED_ONE_SHOT_RECV =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    static final java.util.List<TimedOneShotPacketListener> TIMED_ONE_SHOT_SEND =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    public static MacroExecutor.OneShotPacketListener awaitReceive(java.util.function.Predicate<Packet<?>> match) {
        return new MacroExecutor.OneShotPacketListener(ONE_SHOT_RECV, match);
    }

    public static MacroExecutor.OneShotPacketListener awaitSend(java.util.function.Predicate<Packet<?>> match) {
        return new MacroExecutor.OneShotPacketListener(ONE_SHOT_SEND, match);
    }

    static TimedOneShotPacketListener awaitTimedReceive(TimedPacketMatch match) {
        return new TimedOneShotPacketListener(TIMED_ONE_SHOT_RECV, match);
    }

    static TimedOneShotPacketListener awaitTimedSend(java.util.function.Predicate<Packet<?>> match) {
        return awaitTimedSend((packet, direction, nanoTime) -> match.test(packet));
    }

    static TimedOneShotPacketListener awaitTimedSend(TimedPacketMatch match) {
        return new TimedOneShotPacketListener(TIMED_ONE_SHOT_SEND, match);
    }

    private static void fireOneShotObservers(java.util.List<MacroExecutor.OneShotPacketListener> list, Packet<?> packet) {
        if (list.isEmpty()) return;

        long now = System.nanoTime();
        java.util.ArrayList<MacroExecutor.OneShotPacketListener> matched = null;
        for (MacroExecutor.OneShotPacketListener listener : list) {
            boolean drop;
            try { drop = listener.offer(packet, now); }
            catch (Throwable ignored) { drop = true; }
            if (drop) {
                if (matched == null) matched = new java.util.ArrayList<>(4);
                matched.add(listener);
            }
        }
        if (matched != null) list.removeAll(matched);
    }

    private static void fireTimedObservers(java.util.List<TimedOneShotPacketListener> list, MacroExecutor.MacroEvent event) {
        if (list.isEmpty()) return;
        java.util.ArrayList<TimedOneShotPacketListener> matched = null;
        for (TimedOneShotPacketListener listener : list) {
            boolean drop;
            try { drop = listener.offer(event); }
            catch (Throwable ignored) { drop = true; }
            if (drop) {
                if (matched == null) matched = new java.util.ArrayList<>(4);
                matched.add(listener);
            }
        }
        if (matched != null) list.removeAll(matched);
    }

    public static void drainOneShotListeners() {
        for (MacroExecutor.OneShotPacketListener listener : ONE_SHOT_RECV) listener.cancel();
        for (MacroExecutor.OneShotPacketListener listener : ONE_SHOT_SEND) listener.cancel();
        for (TimedOneShotPacketListener listener : TIMED_ONE_SHOT_RECV) listener.cancel();
        for (TimedOneShotPacketListener listener : TIMED_ONE_SHOT_SEND) listener.cancel();
    }

    public static String oneShotCensus() {
        return "runs=" + MacroExecutor.getActiveRunCount()
            + " recv=" + ONE_SHOT_RECV.size()
            + " send=" + ONE_SHOT_SEND.size()
            + " timedRecv=" + TIMED_ONE_SHOT_RECV.size()
            + " timedSend=" + TIMED_ONE_SHOT_SEND.size();
    }

    public static void onPacketSent(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        if (!TIMED_ONE_SHOT_SEND.isEmpty()) {
            fireTimedObservers(TIMED_ONE_SHOT_SEND, new MacroExecutor.MacroEvent(packet, "C2S", System.nanoTime(), null));
        }
        fireOneShotObservers(ONE_SHOT_SEND, packet);
        onPacketObserved(packet, "C2S");
    }

    public static void onPacketReceived(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        if (!TIMED_ONE_SHOT_RECV.isEmpty()) {
            fireTimedObservers(TIMED_ONE_SHOT_RECV, new MacroExecutor.MacroEvent(packet, "S2C", System.nanoTime(), null));
        }
        fireOneShotObservers(ONE_SHOT_RECV, packet);

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundRespawnPacket
            || packet instanceof net.minecraft.network.protocol.game.ClientboundLoginPacket) {
            MacroConditionRegistry.onRespawnPacket();
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket) {
            MacroConditionRegistry.onTeleportPacket();
        }
        onPacketObserved(packet, "S2C");
    }

    public static void onPacketObserved(Packet<?> packet, String direction) {
        if (PackHideState.isHardLocked()) return;
        MacroChatObserve.syncRecentChatServerContext();

        java.util.List<MacroExecutor.RunState> runs = MacroExecutor.activeRunSnapshot();
        boolean s2c = "S2C".equals(direction);
        boolean chatWork = false;
        boolean mineWork = false;
        boolean packetWork = false;
        for (int i = 0; i < runs.size(); i++) {
            MacroExecutor.RunState run = runs.get(i);
            if (!run.running.get()) continue;
            if (run.waitingForChat.get()) chatWork = true;
            if (run.activeMineTracker != null) mineWork = true;
            if (run.waitingForPacket.get()) packetWork = true;
        }

        if (s2c && (chatWork || MacroExecutor.isWaitingForChat.get())) {
            MacroExecutor.ChatCapture capture = MacroChatObserve.extractIncomingChat(packet);
            if (capture != null && capture.message() != null && !capture.message().isBlank()) {

                for (MacroExecutor.RunState run : runs) {
                    var matched = run.running.get() && run.waitingForChat.get()
                        ? MacroChatObserve.captureChatAction(run.waitingChatAction, capture, run.variables)
                        : java.util.Optional.<java.util.Map<String, MacroValue>>empty();
                    if (matched.isPresent()) {
                        run.stagedCaptures.put(run.waitingChatAction, matched.get());
                        CompletableFuture<Void> f = run.chatFuture;
                        if (f != null) f.complete(null);
                    }
                }
                if (MacroExecutor.isCurrentRunActive() && MacroExecutor.isWaitingForChat.get() && MacroChatObserve.matchesWaitingChat(capture)) {
                    MacroExecutor.lastMatchedChat.set(capture.displayText());
                    CompletableFuture<Void> f = MacroExecutor.activeChatFuture;
                    if (f != null) f.complete(null);
                }
            }
        }

        if (s2c && mineWork) {
            Minecraft mc = Minecraft.getInstance();
            for (MacroExecutor.RunState run : runs) {
                MacroWorldActions.MineProgressTracker tracker = run.running.get() ? run.activeMineTracker : null;
                if (tracker != null) tracker.observePacket(mc, packet);
            }
        }

        if (!MacroExecutor.isCurrentRunActive()) return;

        if (packetWork) {
            for (MacroExecutor.RunState run : runs) {
                if (!run.running.get() || !run.waitingForPacket.get()) continue;
                CompletableFuture<Void> f = null;
                synchronized (run.packetWaitLock) {
                    if (run.waitingForPacket.get() && MacroPacketWait.matchesPacketTarget(run.waitingPacketName.get(), packet, direction)) {
                        run.lastMatchedPacket = packet;
                        run.lastMatchedPacketDirection = direction;
                        f = run.packetFuture;
                    }
                }
                if (f != null) f.complete(null);
            }
        }

        if (MacroExecutor.isWaitingForPacket.get()) {
            CompletableFuture<Void> f = null;
            synchronized (MacroExecutor.packetWaitLock) {
                if (!MacroExecutor.isWaitingForPacket.get()) return;

                String target = MacroExecutor.waitingPacketName.get();
                if (MacroPacketWait.matchesPacketTarget(target, packet, direction)) {
                    MacroExecutor.lastReceivedPacket.set(direction + " " + AutismPacketNamer.getFriendlyName(packet, direction));
                    f = MacroExecutor.activePacketFuture;
                }
            }
            if (f != null) f.complete(null);
        }
    }
}
