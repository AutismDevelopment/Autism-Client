package autismclient.util;

import java.util.HashMap;
import java.util.Map;

public final class AutismTheme {
    public enum Channel { ACCENT, OUTLINE, TEXT, TOGGLE, BACKDROP, SUCCESS, DANGER, BUTTON, HEADER, HOVER }

    public static final int[] DEFAULTS = {
        0xFFFF3B3B,
        0xFFB32B2B,
        0xFFF3ECE7,
        0xFFFF3B3B,
        0xFFB24848,
        0xFF35D873,
        0xFFE26A6A,
        0xFF8F1F24,
        0xFFFF3B3B,
        0xFFFF6464
    };

    private static final float NEUTRAL_THRESHOLD = 0.10f;
    private static final float RED_BAND = 0.092f;
    private static final float FH = 10.0f / 360.0f;

    private static final float PURPLE_BAND_MIN = 195.0f / 360.0f;
    private static final float PURPLE_BAND_MAX = 315.0f / 360.0f;

    private static volatile State active;
    private static final Map<Long, Integer> CACHE = new HashMap<>(512);

    private AutismTheme() {}

    public static final class State {
        public final boolean advanced;
        public final boolean anyActive;
        final boolean[] activeChannel = new boolean[Channel.values().length];
        final float[] hue = new float[Channel.values().length];
        final float[] sat = new float[Channel.values().length];
        final float[] val = new float[Channel.values().length];
        final float[] alpha = new float[Channel.values().length];

        private State(AutismConfig.ThemeColors cfg) {
            this.advanced = cfg.advanced;
            int[] targets = new int[Channel.values().length];
            if (cfg.advanced) {
                targets[Channel.ACCENT.ordinal()]   = cfg.accent;
                targets[Channel.OUTLINE.ordinal()]  = cfg.outline;
                targets[Channel.TEXT.ordinal()]     = cfg.text;
                targets[Channel.TOGGLE.ordinal()]   = cfg.toggle;
                targets[Channel.BACKDROP.ordinal()] = cfg.backdrop;
                targets[Channel.SUCCESS.ordinal()]  = cfg.success;
                targets[Channel.DANGER.ordinal()]   = cfg.danger;
                targets[Channel.BUTTON.ordinal()]   = cfg.button;
                targets[Channel.HEADER.ordinal()]   = cfg.header;
                targets[Channel.HOVER.ordinal()]    = cfg.hover;
            } else {

                targets[Channel.ACCENT.ordinal()]   = cfg.master;
                targets[Channel.OUTLINE.ordinal()]  = cfg.master;
                targets[Channel.TOGGLE.ordinal()]   = cfg.master;
                targets[Channel.BACKDROP.ordinal()] = cfg.master;
                targets[Channel.BUTTON.ordinal()]   = cfg.master;
                targets[Channel.DANGER.ordinal()]   = cfg.master;
                targets[Channel.HEADER.ordinal()]   = cfg.master;
                targets[Channel.HOVER.ordinal()]    = cfg.master;
                targets[Channel.TEXT.ordinal()]     = DEFAULTS[Channel.TEXT.ordinal()];
                targets[Channel.SUCCESS.ordinal()]  = DEFAULTS[Channel.SUCCESS.ordinal()];
            }

            int defaultMaster = DEFAULTS[Channel.ACCENT.ordinal()];
            boolean any = false;
            for (int i = 0; i < targets.length; i++) {
                float[] hsb = java.awt.Color.RGBtoHSB((targets[i] >> 16) & 0xFF, (targets[i] >> 8) & 0xFF, targets[i] & 0xFF, null);
                hue[i] = hsb[0];
                sat[i] = hsb[1];
                val[i] = hsb[2];
                alpha[i] = (((targets[i] >>> 24) & 0xFF)) / 255.0f;

                int baseline = cfg.advanced || i == Channel.TEXT.ordinal() || i == Channel.SUCCESS.ordinal()
                    ? DEFAULTS[i] : defaultMaster;
                boolean rgbDiff = (targets[i] & 0x00FFFFFF) != (baseline & 0x00FFFFFF);
                boolean alphaDiff = ((targets[i] >>> 24) & 0xFF) != ((baseline >>> 24) & 0xFF);
                activeChannel[i] = rgbDiff || alphaDiff;
                any |= activeChannel[i];
            }
            this.anyActive = any;
        }

        public static State from(AutismConfig.ThemeColors cfg) {
            return new State(cfg);
        }

        public boolean isActive(Channel ch) {
            return activeChannel[ch.ordinal()];
        }

        public float hueOf(Channel ch) {
            return hue[ch.ordinal()];
        }

        public float satOf(Channel ch) {
            return sat[ch.ordinal()];
        }

        public int colorOf(Channel ch) {
            int i = ch.ordinal();
            int rgb = java.awt.Color.HSBtoRGB(hue[i], sat[i], val[i]) & 0x00FFFFFF;
            return (Math.round(alpha[i] * 255.0f) << 24) | rgb;
        }

        public int previewSignature(Channel ch) {
            int i = ch.ordinal();
            if (!activeChannel[i]) return 0;
            int sig = Float.floatToIntBits(hue[i]);
            sig = sig * 31 + Float.floatToIntBits(sat[i]);
            sig = sig * 31 + Float.floatToIntBits(val[i]);
            sig = sig * 31 + Float.floatToIntBits(alpha[i]);
            return sig == 0 ? 1 : sig;
        }
    }

    private static float hueDistance(float a, float b) {
        float d = Math.abs(a - b);
        return Math.min(d, 1.0f - d);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float shortestArc(float a, float b) {
        return ((b - a + 1.5f) % 1.0f) - 0.5f;
    }

    private static float wrapHue(float h) {
        return h - (float) Math.floor(h);
    }

    public static State active() {
        State s = active;
        if (s == null) {

            s = new State(AutismLiteVariant.enabled()
                ? new AutismConfig.ThemeColors()
                : AutismConfig.getGlobal().themeColors);
            active = s;
        }
        return s;
    }

    public static boolean isCustomized() {
        return active().anyActive;
    }

    private static volatile int generation;

    public static int generation() {
        return generation;
    }

    public static void reload() {

        if (AutismLiteVariant.enabled()) return;
        active = new State(AutismConfig.getGlobal().themeColors);
        generation++;
        synchronized (CACHE) { CACHE.clear(); }
        try { autismclient.gui.vanillaui.UiContexts.refreshTheme(); } catch (Throwable ignored) {  }
        try { AutismThemeTextures.invalidate(); } catch (Throwable ignored) {  }
    }

    public static int recolor(int argb) {
        return recolor(argb, Channel.ACCENT);
    }

    public static int recolor(int argb, Channel ch) {
        State st = active();
        if (!st.anyActive || !st.activeChannel[ch.ordinal()]) return argb;
        long key = ((long) ch.ordinal() << 56) | (argb & 0xFFFFFFFFL);
        synchronized (CACHE) {
            Integer cached = CACHE.get(key);
            if (cached != null) return cached;
            int out = recolor(argb, ch, st);
            CACHE.put(key, out);
            return out;
        }
    }

    public static int recolor(int argb, Channel ch, State st) {
        int ci = ch.ordinal();
        if (st == null || !st.activeChannel[ci]) return argb;
        int a = Math.round(((argb >>> 24) & 0xFF) * st.alpha[ci]);
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        float s = hsb[1], v = hsb[2];
        float th = st.hue[ci], ts = st.sat[ci], tv = st.val[ci];

        if (ch == Channel.TEXT) {

            float outS = Math.min(0.40f, s + ts * 0.30f);
            return (a << 24) | (java.awt.Color.HSBtoRGB(th, outS, v) & 0x00FFFFFF);
        }
        if (s < NEUTRAL_THRESHOLD) return (a << 24) | (argb & 0x00FFFFFF);
        float outS = s * ts;
        float outV = clamp01(v + (tv - v) * (1.0f - ts) * 0.7f);
        return (a << 24) | (java.awt.Color.HSBtoRGB(th, outS, outV) & 0x00FFFFFF);
    }

    private static float clamp01(float v) { return Math.max(0.0f, Math.min(1.0f, v)); }

    public static int recolorImagePixel(int argb, Channel ch, State st) {
        int ci = ch.ordinal();
        if (st == null || !st.activeChannel[ci]) return argb;
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return argb;
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        float h = hsb[0], s = hsb[1], v = hsb[2];
        float w = satWeight(s) * bandWeight(h, ch);
        if (w <= 0.0f) return argb;

        float outS = s * st.sat[ci];
        float hOut = wrapHue(h + shortestArc(h, st.hue[ci]) * w);
        float sOut = s + (outS - s) * w;
        return (a << 24) | (java.awt.Color.HSBtoRGB(hOut, sOut, v) & 0x00FFFFFF);
    }

    private static float satWeight(float s) {
        return smoothstep(0.12f, 0.24f, s);
    }

    private static float bandWeight(float h, Channel ch) {
        if (ch == Channel.BACKDROP) return purpleBandWeight(h);
        return 1.0f - smoothstep(RED_BAND, RED_BAND + FH, hueDistance(h, 0.0f));
    }

    private static float purpleBandWeight(float h) {
        return smoothstep(PURPLE_BAND_MIN - FH, PURPLE_BAND_MIN, h)
            * (1.0f - smoothstep(PURPLE_BAND_MAX, PURPLE_BAND_MAX + FH, h));
    }

    public static int recolorImagePixelTo(int argb, float targetHue, float targetSat) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return argb;
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        float h = hsb[0], s = hsb[1], v = hsb[2];
        float w = satWeight(s) * purpleBandWeight(h);
        if (w <= 0.0f) return argb;

        float outS = clamp01(targetSat * (0.65f + 0.35f * s));
        float hOut = wrapHue(h + shortestArc(h, targetHue) * w);
        float sOut = s + (outS - s) * w;
        return (a << 24) | (java.awt.Color.HSBtoRGB(hOut, sOut, v) & 0x00FFFFFF);
    }
}
