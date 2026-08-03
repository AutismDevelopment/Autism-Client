package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Toggle {
    private static final long ANIM_NANOS = 150_000_000L;
    private static final long STALE_NANOS = 30_000_000_000L;
    private static final int OFF_TRACK = 0xFF3A3A40;
    private static final int KNOB = 0xFFE9E9EC;
    private static final Map<String, State> STATES = new HashMap<>();
    private static long lastPrune;

    private Toggle() {
    }

    public static void render(UiContext context, UiBounds bounds, boolean enabled, boolean hovered) {
        render(context, bounds, enabled, hovered, bounds.x() + ":" + bounds.y() + ":" + bounds.width());
    }

    public static void render(UiContext context, UiBounds bounds, boolean enabled, boolean hovered, String animationKey) {
        renderProgress(context, bounds, easedProgress(animationKey, enabled), hovered);
    }

    public static void renderProgress(UiContext context, UiBounds bounds, float progress, boolean hovered) {
        float eased = progress * progress * (3.0F - 2.0F * progress);
        GuiGraphicsExtractor graphics = context.graphics();
        int track = lerpColor(OFF_TRACK, context.theme().colors().accent, eased);
        if (hovered) track = lift(track, 0.12F);

        UiRenderer.roundRect(graphics, bounds, bounds.height() / 2, track);

        float r = Math.max(1.5F, bounds.height() / 2.0F - 1.0F);
        float cxLeft = bounds.x() + 1.0F + r;
        float cxRight = bounds.right() - 1.0F - r;
        float cx = cxLeft + (cxRight - cxLeft) * eased;
        UiRenderer.disc(graphics, cx, bounds.y() + bounds.height() / 2.0F, r, KNOB);
    }

    public static void renderLabeled(UiContext context, UiBounds bounds, String label, boolean enabled, boolean hovered, String animationKey) {
        var colors = context.theme().colors();
        int pillW = Math.min(26, Math.max(20, bounds.height() * 2 - 4));
        int pillH = Math.min(12, Math.max(9, bounds.height() - 6));
        UiBounds pill = UiBounds.of(bounds.right() - pillW - 2, bounds.y() + Math.max(0, (bounds.height() - pillH) / 2), pillW, pillH);
        context.text().drawFitted(context.graphics(), label == null ? "" : label,
            bounds.x() + 5, context.text().centeredY(bounds), Math.max(1, pill.x() - bounds.x() - 9),
            enabled ? colors.text : colors.muted);
        render(context, pill, enabled, hovered, animationKey);
    }

    private static float easedProgress(String key, boolean target) {
        float t = progress(key, target);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float progress(String key, boolean target) {
        long now = System.nanoTime();
        State state = STATES.get(key);
        if (state == null) {
            state = new State(target ? 1.0F : 0.0F, target, now);
            STATES.put(key, state);
            return state.value(now);
        }
        if (state.target != target) {
            state.start = state.value(now);
            state.target = target;
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

    private static final class State {
        float start;
        boolean target;
        long since;
        long lastSeen;

        State(float start, boolean target, long now) {
            this.start = start;
            this.target = target;
            this.since = now;
            this.lastSeen = now;
        }

        float value(long now) {
            float elapsed = Math.min(1.0F, (now - since) / (float) ANIM_NANOS);
            float eased = elapsed * elapsed * (3.0F - 2.0F * elapsed);
            return start + ((target ? 1.0F : 0.0F) - start) * eased;
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int ar = (from >> 16) & 0xFF, ag = (from >> 8) & 0xFF, ab = from & 0xFF, aa = (from >>> 24) & 0xFF;
        int br = (to >> 16) & 0xFF, bg = (to >> 8) & 0xFF, bb = to & 0xFF, ba = (to >>> 24) & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b = Math.round(ab + (bb - ab) * t);
        int a = Math.round(aa + (ba - aa) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lift(int color, float amount) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r += Math.round((255 - r) * amount);
        g += Math.round((255 - g) * amount);
        b += Math.round((255 - b) * amount);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
