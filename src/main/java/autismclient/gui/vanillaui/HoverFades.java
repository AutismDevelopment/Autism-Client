package autismclient.gui.vanillaui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class HoverFades {
    private static final long DURATION_NANOS = 130_000_000L;
    private static final long STALE_NANOS = 30_000_000_000L;
    private static final Map<String, State> STATES = new HashMap<>();
    private static long lastPrune;

    private HoverFades() {
    }

    public static float get(String key, boolean hovered) {
        long now = System.nanoTime();
        State state = STATES.get(key);
        if (state == null) {
            state = new State(hovered ? 1.0F : 0.0F, hovered, now);
            STATES.put(key, state);
            return state.value;
        }
        if (state.target != hovered) {
            state.start = state.value(now);
            state.target = hovered;
            state.since = now;
        }
        state.lastSeen = now;
        if (now - lastPrune > STALE_NANOS) {
            lastPrune = now;
            Iterator<State> it = STATES.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().lastSeen > STALE_NANOS) it.remove();
            }
        }
        return state.value(now);
    }

    public static String key(UiBounds bounds) {
        return bounds.x() + ":" + bounds.y() + ":" + bounds.width() + ":" + bounds.height();
    }

    private static final class State {
        float start;
        float value;
        boolean target;
        long since;
        long lastSeen;

        State(float value, boolean target, long now) {
            this.value = value;
            this.start = value;
            this.target = target;
            this.since = now;
            this.lastSeen = now;
        }

        float value(long now) {
            float elapsed = Math.min(1.0F, (now - since) / (float) DURATION_NANOS);
            float eased = elapsed * elapsed * (3.0F - 2.0F * elapsed);
            value = start + ((target ? 1.0F : 0.0F) - start) * eased;
            return value;
        }
    }
}
