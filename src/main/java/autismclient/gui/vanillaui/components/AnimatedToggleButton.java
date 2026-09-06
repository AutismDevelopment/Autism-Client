package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class AnimatedToggleButton {
    private static final int SLOW_WIDTH = 40;
    private static final int FAST_WIDTH = 96;
    private static final long SLOW_ANIMATION_NANOS = 205_000_000L;
    private static final long FAST_ANIMATION_NANOS = 128_000_000L;

    private static final long FADE_NANOS = 200_000_000L;
    private static final long STALE_NANOS = 30_000_000_000L;
    private static final Map<String, State> STATES = new HashMap<>();
    private static long lastPruneNanos;

    private AnimatedToggleButton() {
    }

    public static void render(
        UiContext context,
        UiBounds bounds,
        String label,
        boolean enabled,
        boolean hovered,
        String animationKey
    ) {
        long now = System.nanoTime();
        State state = STATES.get(animationKey);
        if (state == null) {
            state = new State(enabled ? 1.0F : 0.0F, enabled, now);
            STATES.put(animationKey, state);
        } else if (state.targetEnabled != enabled) {
            state.startProgress = state.progress(now, FADE_NANOS);
            state.targetEnabled = enabled;
            state.changedNanos = now;
        }
        state.lastSeenNanos = now;
        renderStateButton(context, bounds, label, hovered, state.progress(now, FADE_NANOS));
        pruneIfNeeded(now);
    }

    private static void renderStateButton(UiContext context, UiBounds bounds, String label, boolean hovered, float progress) {
        var colors = context.theme().colors();
        float p = clamp01(progress);

        UiRenderer.frame(context.graphics(), bounds, 0xC01E242D, colors.buttonBorder);
        if (p > 0.001f) {
            UiRenderer.rect(context.graphics(), bounds.inset(1),
                UiRenderer.applyAlpha(autismclient.util.AutismTheme.recolor(0xD41F5233, autismclient.util.AutismTheme.Channel.SUCCESS), p));
            UiRenderer.outline(context.graphics(), bounds, UiRenderer.applyAlpha(colors.success, p));
        }
        float hoverT = autismclient.gui.vanillaui.HoverFades.get(autismclient.gui.vanillaui.HoverFades.key(bounds), hovered);
        if (hoverT > 0.001f) {
            UiRenderer.rect(context.graphics(), bounds.inset(1),
                (((int) Math.round(0x14 * hoverT)) << 24) | 0x00FFFFFF);
        }
        if (label != null && !label.isEmpty()) {
            context.text().drawCentered(context.graphics(), label, bounds, colors.text);
        }
    }

    public static long durationNanos(int width) {
        float t = clamp01((width - SLOW_WIDTH) / (float) Math.max(1, FAST_WIDTH - SLOW_WIDTH));
        return Math.round(SLOW_ANIMATION_NANOS + ((FAST_ANIMATION_NANOS - SLOW_ANIMATION_NANOS) * t));
    }

    private static void pruneIfNeeded(long now) {
        if (now - lastPruneNanos < STALE_NANOS) {
            return;
        }
        lastPruneNanos = now;
        Iterator<State> iterator = STATES.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().lastSeenNanos > STALE_NANOS) {
                iterator.remove();
            }
        }
    }

    private static final class State {
        private float startProgress;
        private boolean targetEnabled;
        private long changedNanos;
        private long lastSeenNanos;

        private State(float startProgress, boolean targetEnabled, long now) {
            this.startProgress = startProgress;
            this.targetEnabled = targetEnabled;
            this.changedNanos = now;
            this.lastSeenNanos = now;
        }

        private float progress(long now, long durationNanos) {
            float elapsed = Math.min(1.0F, (now - changedNanos) / (float) Math.max(1L, durationNanos));
            float eased = elapsed * elapsed * (3.0F - (2.0F * elapsed));
            float target = targetEnabled ? 1.0F : 0.0F;
            return startProgress + ((target - startProgress) * eased);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
