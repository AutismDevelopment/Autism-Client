package autismclient.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AutismPayloadStudySession {
    private static final int MAX_FINGERPRINTS = 512;
    private static final int MAX_SUMMARY_CHANNELS = 6;

    private static volatile boolean active;
    private static String label = "";
    private static long startedAtMs;
    private static final List<String> patterns = new ArrayList<>();
    private static final LinkedHashSet<String> armedChannels = new LinkedHashSet<>();
    private static final LinkedHashMap<String, ChannelStats> channels = new LinkedHashMap<>();
    private static final LinkedHashSet<String> registeredHints = new LinkedHashSet<>();
    private static final LinkedHashSet<String> fingerprints = new LinkedHashSet<>();
    private static int eventCount;
    private static int matchedCount;
    private static int registerListCount;

    private AutismPayloadStudySession() {
    }

    public static synchronized void start(String studyLabel, Collection<String> studyPatterns, Collection<String> registeredNow) {
        active = true;
        label = studyLabel == null || studyLabel.isBlank() ? "Payloads" : studyLabel.trim();
        startedAtMs = System.currentTimeMillis();
        patterns.clear();
        armedChannels.clear();
        channels.clear();
        registeredHints.clear();
        fingerprints.clear();
        eventCount = 0;
        matchedCount = 0;
        registerListCount = 0;

        if (studyPatterns != null) {
            for (String pattern : studyPatterns) {
                String normalized = AutismPayloadChannelListeners.normalizePattern(pattern);
                if (!normalized.isBlank() && !patterns.contains(normalized)) patterns.add(normalized);
            }
        }
        if (registeredNow != null) {
            for (String channel : registeredNow) {
                String normalized = AutismPayloadChannelRegistrations.normalizeChannel(channel);
                if (AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) armedChannels.add(normalized);
            }
        }
        AutismNetworkCaptureState.refreshCurrent();
    }

    public static boolean isActive() {
        return active;
    }

    public static synchronized String bannerTitle() {
        return active ? "Payload Study: " + label : "";
    }

    public static synchronized String bannerLine1() {
        if (!active) return "";
        return eventCount + " payloads | " + channels.size() + " channels | ESC to finish";
    }

    public static synchronized String bannerLine2() {
        if (!active) return "";
        if (channels.isEmpty()) return "Interact with the plugin GUI/commands now.";
        List<ChannelStats> sorted = new ArrayList<>(channels.values());
        sorted.sort((a, b) -> Integer.compare(b.count, a.count));
        StringBuilder sb = new StringBuilder("Top: ");
        int shown = 0;
        for (ChannelStats stats : sorted) {
            if (shown >= 3) break;
            if (shown > 0) sb.append(", ");
            sb.append(stats.channel).append(" x").append(stats.count);
            shown++;
        }
        if (sorted.size() > shown) sb.append(" +").append(sorted.size() - shown);
        return sb.toString();
    }

    public static synchronized void stop() {
        active = false;
        label = "";
        startedAtMs = 0L;
        patterns.clear();
        armedChannels.clear();
        channels.clear();
        registeredHints.clear();
        fingerprints.clear();
        eventCount = 0;
        matchedCount = 0;
        registerListCount = 0;
        AutismNetworkCaptureState.refreshCurrent();
    }

    public static synchronized boolean recordPayload(AutismPayloadSupport.PayloadSnapshot snapshot) {
        if (!active || snapshot == null) return false;
        String channel = AutismPayloadChannelRegistrations.normalizeChannel(snapshot.channel());
        if (channel.isBlank()) return false;

        String fingerprint = fingerprint(snapshot);
        if (!fingerprint.isBlank()) {
            if (!fingerprints.add(fingerprint)) return false;
            while (fingerprints.size() > MAX_FINGERPRINTS) {
                String first = fingerprints.iterator().next();
                fingerprints.remove(first);
            }
        }

        AutismPayloadChannelSubscriptionManager.rememberObservedPayload(snapshot);
        eventCount++;
        ChannelStats stats = channels.computeIfAbsent(channel, ChannelStats::new);
        stats.count++;
        stats.totalBytes += Math.max(0, snapshot.sizeBytes());
        String direction = snapshot.direction() == null ? "" : snapshot.direction().trim().toUpperCase(Locale.ROOT);
        if ("C2S".equals(direction)) stats.c2s++;
        else if ("S2C".equals(direction)) stats.s2c++;

        boolean matched = patterns.isEmpty();
        for (String pattern : patterns) {
            if (AutismPayloadChannelListeners.patternMatchesChannel(pattern, channel)) {
                stats.matchedPatterns.add(pattern);
                matched = true;
            }
        }
        if (matched) matchedCount++;

        List<String> registerChannels = AutismPayloadSupport.extractRegisterChannelList(channel, snapshot.rawBytes());
        if (!registerChannels.isEmpty()) {
            registerListCount++;
            for (String hinted : registerChannels) {
                String normalized = AutismPayloadChannelRegistrations.normalizeChannel(hinted);
                if (AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) registeredHints.add(normalized);
            }
        }

        return true;
    }

    public static synchronized boolean finishFromEscape() {
        if (!active) return false;
        Summary summary = finish();
        if (summary.eventCount() == 0) {
            AutismNotifications.warning("Payload study finished: no payloads captured.");
            return true;
        }
        AutismNotifications.copied("Payload study: " + summary.eventCount() + " payloads, " + summary.channelCount() + " channels.");
        if (!summary.topChannels().isBlank()) {
            AutismNotifications.show(summary.topChannels(), 0xFF8FE8B0);
        }
        if (summary.learnedCount() > 0) {
            AutismNotifications.warning("Learned " + summary.learnedCount() + " exact channels. Open Channels -> Capture.");
        }
        return true;
    }

    public static synchronized Summary finish() {
        long durationMs = active ? Math.max(0L, System.currentTimeMillis() - startedAtMs) : 0L;
        active = false;
        List<ChannelStats> sorted = new ArrayList<>(channels.values());
        sorted.sort((a, b) -> {
            int count = Integer.compare(b.count, a.count);
            return count != 0 ? count : a.channel.compareToIgnoreCase(b.channel);
        });
        StringBuilder top = new StringBuilder();
        int shown = 0;
        for (ChannelStats stats : sorted) {
            if (shown >= MAX_SUMMARY_CHANNELS) break;
            if (!top.isEmpty()) top.append(", ");
            top.append(stats.channel).append(" x").append(stats.count);
            shown++;
        }
        if (sorted.size() > shown) top.append(" +").append(sorted.size() - shown);
        int learned = learnedExactChannelsLocked().size();
        Summary summary = new Summary(label, durationMs, eventCount, channels.size(), matchedCount,
            registerListCount, learned, top.toString());
        AutismNetworkCaptureState.refreshCurrent();
        return summary;
    }

    public static synchronized List<String> learnedExactChannels() {
        return List.copyOf(learnedExactChannelsLocked());
    }

    private static LinkedHashSet<String> learnedExactChannelsLocked() {
        LinkedHashSet<String> learned = new LinkedHashSet<>();
        learned.addAll(channels.keySet());
        learned.addAll(registeredHints);
        learned.removeAll(armedChannels);
        return learned;
    }

    private static String fingerprint(AutismPayloadSupport.PayloadSnapshot snapshot) {
        byte[] bytes = snapshot.rawBytes();
        return (snapshot.direction() == null ? "" : snapshot.direction()) + '|'
            + (snapshot.protocolPhase() == null ? "" : snapshot.protocolPhase()) + '|'
            + snapshot.packetId() + '|'
            + (snapshot.channel() == null ? "" : snapshot.channel()) + '|'
            + snapshot.sizeBytes() + '|'
            + Arrays.hashCode(bytes);
    }

    private static final class ChannelStats {
        private final String channel;
        private final Set<String> matchedPatterns = new LinkedHashSet<>();
        private int count;
        private int c2s;
        private int s2c;
        private long totalBytes;

        private ChannelStats(String channel) {
            this.channel = channel;
        }
    }

    public record Summary(String label, long durationMs, int eventCount, int channelCount, int matchedCount,
                          int registerListCount, int learnedCount, String topChannels) {
    }
}
