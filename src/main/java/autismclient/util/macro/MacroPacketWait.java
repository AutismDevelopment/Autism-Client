package autismclient.util.macro;

import autismclient.util.AutismPacketNamer;
import autismclient.util.AutismPacketRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.concurrent.CompletableFuture;

final class MacroPacketWait {
    private MacroPacketWait() {
    }
    private static String normalizePacketKey(String value) {

        return AutismPacketNamer.normalizePacketKey(value);
    }

    private static boolean packetKeyMatches(String normalizedExpected, String candidate) {
        if (normalizedExpected.isEmpty() || candidate == null || candidate.isEmpty()) return false;

        String normalizedCandidate = normalizePacketKey(candidate);
        if (normalizedCandidate.isEmpty()) return false;
        if (normalizedExpected.equals(normalizedCandidate)) return true;

        if (normalizedCandidate.endsWith("packet")) {
            String strippedCandidate = normalizedCandidate.substring(0, normalizedCandidate.length() - "packet".length());
            if (normalizedExpected.equals(strippedCandidate)) return true;
        }

        return normalizedExpected.endsWith(normalizedCandidate) || normalizedCandidate.endsWith(normalizedExpected);
    }

    private static boolean packetNameMatches(String expected, String candidate) {
        if (expected == null || expected.isEmpty()) return false;
        return packetKeyMatches(normalizePacketKey(expected), candidate);
    }

    private record ParsedPacketTarget(String direction, String expectedName, String normalizedExpected) {}
    private static final java.util.concurrent.ConcurrentHashMap<String, ParsedPacketTarget> PARSED_TARGET_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final ParsedPacketTarget EMPTY_PACKET_TARGET = new ParsedPacketTarget("", "", "");

    private static ParsedPacketTarget parsedPacketTarget(String target) {
        if (target == null) return EMPTY_PACKET_TARGET;
        if (PARSED_TARGET_CACHE.size() > 256) PARSED_TARGET_CACHE.clear();
        return PARSED_TARGET_CACHE.computeIfAbsent(target, t -> {
            String expectedName = WaitForPacketAction.getPacketName(t);
            return new ParsedPacketTarget(WaitForPacketAction.getDirection(t), expectedName, normalizePacketKey(expectedName));
        });
    }

    public static boolean matchesPacketTarget(String target, Packet<?> packet, String direction) {
        if (packet == null) return false;

        ParsedPacketTarget parsed = parsedPacketTarget(target);
        if (!parsed.direction().isEmpty() && !parsed.direction().equalsIgnoreCase(direction)) return false;
        if (parsed.expectedName().isEmpty()) return true;

        String normalizedExpected = parsed.normalizedExpected();
        if (packetKeyMatches(normalizedExpected, AutismPacketNamer.getFriendlyName(packet, direction))) return true;
        if (packetKeyMatches(normalizedExpected, AutismPacketNamer.getFriendlyName(packet))) return true;
        if (packetKeyMatches(normalizedExpected, packet.getClass().getSimpleName())) return true;

        @SuppressWarnings("unchecked")
        String fullName = AutismPacketRegistry.getName((Class<? extends Packet<?>>) packet.getClass());
        return packetKeyMatches(normalizedExpected, fullName);
    }

    public static void awaitPacket(String target) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run != null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            String normalizedTarget = WaitForPacketAction.normalizeTarget(target);
            synchronized (run.packetWaitLock) {
                run.waitingPacketName.set(normalizedTarget);
                run.packetFuture = future;
                run.waitingForPacket.set(true);
            }
            try {
                MacroExecutor.setCurrentStatus(normalizedTarget.isEmpty()
                    ? "Waiting for packet: Any"
                    : "Waiting for packet: " + WaitForPacketAction.getDisplayLabel(normalizedTarget));
                MacroRunLoop.waitForCondition(future);
            } finally {
                synchronized (run.packetWaitLock) {
                    if (run.packetFuture == future) {
                        run.waitingForPacket.set(false);
                        run.waitingPacketName.set("");
                        run.packetFuture = null;
                    }
                }
            }
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        String normalizedTarget = WaitForPacketAction.normalizeTarget(target);
        synchronized (MacroExecutor.packetWaitLock) {
            MacroExecutor.waitingPacketName.set(normalizedTarget);
            MacroExecutor.lastReceivedPacket.set("");
            MacroExecutor.activePacketFuture = future;
            MacroExecutor.isWaitingForPacket.set(true);
        }
        try {
            MacroExecutor.setCurrentStatus(normalizedTarget.isEmpty()
                ? "Waiting for packet: Any"
                : "Waiting for packet: " + WaitForPacketAction.getDisplayLabel(normalizedTarget));
            MacroRunLoop.waitForCondition(future);
        } finally {
            synchronized (MacroExecutor.packetWaitLock) {
                if (MacroExecutor.activePacketFuture == future) {
                    MacroExecutor.isWaitingForPacket.set(false);
                    MacroExecutor.waitingPacketName.set("");
                    MacroExecutor.activePacketFuture = null;
                }
            }
        }
    }

    public static void awaitChat(WaitForChatAction wca) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run != null) {
            MacroPrearm.clearCaptureOutputs(wca);
            CompletableFuture<Void> future = new CompletableFuture<>();
            run.chatFuture = future;
            run.waitingChatAction = wca;
            run.waitingForChat.set(true);
            try {
                if (wca.timeoutMs > 0) future.completeOnTimeout(null, wca.timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                MacroRunLoop.waitForCondition(future);
                MacroPrearm.commitStagedCapture(wca);
            } finally {
                if (run.chatFuture == future) {
                    run.waitingForChat.set(false);
                    run.waitingChatAction = null;
                    run.chatFuture = null;
                }
            }
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        MacroExecutor.activeChatFuture = future;
        MacroExecutor.isWaitingForChat.set(true);
        MacroExecutor.waitingChatPattern.set(MacroChatText.normalizeManualText(wca.pattern));
        MacroExecutor.waitingChatPatternJson.set(wca.patternJson == null ? "" : wca.patternJson);
        MacroExecutor.waitingChatIsRegex.set(wca.useRegex);
        MacroExecutor.waitingChatFuzzyPercent.set(WaitForChatAction.clampFuzzyPercent(wca.fuzzyPercent));
        MacroExecutor.lastMatchedChat.set("");
        try {
            if (wca.timeoutMs > 0) future.completeOnTimeout(null, wca.timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            MacroRunLoop.waitForCondition(future);
        } finally {
            MacroExecutor.isWaitingForChat.set(false);
            MacroExecutor.waitingChatPattern.set("");
            MacroExecutor.waitingChatPatternJson.set("");
            MacroExecutor.waitingChatIsRegex.set(false);
            MacroExecutor.waitingChatFuzzyPercent.set(100);
            MacroExecutor.activeChatFuture = null;
        }
    }

    public static void awaitCaptureValueChat(CaptureValueAction action) {
        if (action == null) return;
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        MacroPrearm.clearCaptureOutputs(action);
        MacroExecutor.OneShotPacketListener listener = MacroExecutor.awaitReceive(packet -> {
            MacroExecutor.ChatCapture capture = MacroChatObserve.extractIncomingChat(packet);
            if (capture == null) return false;
            MacroExecutor.RecentChatMessage message = new MacroExecutor.RecentChatMessage(
                capture.sender() == null ? "" : capture.sender(),
                capture.message() == null ? "" : capture.message(),
                capture.displayText() == null ? "" : capture.displayText(),
                capture.displayComponent(),
                capture.source(),
                System.currentTimeMillis()
            );
            CaptureValueAction.Preview preview = action.previewChat(message);
            if (!preview.success()) return false;
            if (run != null) run.stagedCaptures.put(action, preview.values());
            else MacroVariables.setAll(preview.values());
            return true;
        });
        try {
            if (MacroRace.waitForFutureDone(listener.future, 0) && run != null) MacroPrearm.commitStagedCapture(action);
        } finally {
            listener.cancel();
        }
    }

    static CaptureListSelector.State captureSelectionState(MacroAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run == null || action == null) return null;
        synchronized (run.captureSelection) {
            return run.captureSelection.computeIfAbsent(action, a -> new CaptureListSelector.State());
        }
    }
}
