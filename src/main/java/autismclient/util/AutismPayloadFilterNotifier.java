package autismclient.util;

import autismclient.modules.PackHideState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AutismPayloadFilterNotifier {
    private static final long THROTTLE_MS = 2000L;
    private static final long STALE_MS = 30000L;
    private static final int ACCENT = 0xFFFFC857;
    private static final Map<String, State> STATES = new LinkedHashMap<>();
    private static volatile boolean hasStates;

    private AutismPayloadFilterNotifier() {
    }

    public static synchronized void onMatch(String channel, String direction, AutismPayloadChannelListeners.Match match) {
        if (!"S2C".equalsIgnoreCase(direction)) return;
        if (PackHideState.isActive()) return;
        String normalized = normalize(channel);
        if (normalized.isBlank()) return;
        long now = System.currentTimeMillis();
        State state = STATES.computeIfAbsent(normalized, unused -> new State());
        hasStates = true;
        state.channel = normalized;
        state.summaryLabel = summaryLabel(normalized, match);
        state.lastSeenMs = now;
        if (now >= state.nextToastMs) {
            if (state.pendingHits > 0) {
                state.pendingHits++;
                showSummary(state);
            } else {
                AutismNotifications.show("Payload: " + normalized, ACCENT);
            }
            state.pendingHits = 0;
            state.nextToastMs = now + THROTTLE_MS;
        } else {
            state.pendingHits++;
        }
    }

    public static void tick() {
        if (!hasStates) return;
        synchronized (AutismPayloadFilterNotifier.class) {
            if (STATES.isEmpty()) {
                hasStates = false;
                return;
            }
            if (PackHideState.isHardLocked()) {
                STATES.clear();
                hasStates = false;
                return;
            }
            long now = System.currentTimeMillis();
            for (Iterator<Map.Entry<String, State>> it = STATES.entrySet().iterator(); it.hasNext();) {
                State state = it.next().getValue();
                if (state.pendingHits > 0 && now >= state.nextToastMs) {
                    showSummary(state);
                    state.pendingHits = 0;
                    state.nextToastMs = now + THROTTLE_MS;
                }
                if (state.pendingHits == 0 && now - state.lastSeenMs > STALE_MS) {
                    it.remove();
                }
            }
            hasStates = !STATES.isEmpty();
        }
    }

    public static synchronized void clear() {
        STATES.clear();
        hasStates = false;
    }

    private static void showSummary(State state) {
        String label = state.summaryLabel == null || state.summaryLabel.isBlank() ? state.channel : state.summaryLabel;
        AutismNotifications.show("Payload: " + label + " +" + state.pendingHits, ACCENT);
    }

    private static String summaryLabel(String channel, AutismPayloadChannelListeners.Match match) {
        if (match == null || match.pattern() == null || match.pattern().isBlank()) return channel;
        String pattern = match.pattern().trim().toLowerCase(Locale.ROOT);
        return pattern.contains("*") ? pattern : channel;
    }

    private static String normalize(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
    }

    private static final class State {
        String channel = "";
        String summaryLabel = "";
        long nextToastMs;
        long lastSeenMs;
        int pendingHits;
    }
}
