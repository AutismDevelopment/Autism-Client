package autismclient.util;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class AutismMarquee {

    public static final int GAP = 36;

    private AutismMarquee() {
    }

    public static int cycleLength(int textWidth) {
        return textWidth + GAP;
    }

    public static float offset(int textWidth, int clipWidth, long nowMs, long holdUntilMs, int speedPxPerSec) {
        if (textWidth <= clipWidth || nowMs < holdUntilMs) return 0.0f;

        return (float) (((nowMs - holdUntilMs) * speedPxPerSec / 1000.0) % cycleLength(textWidth));
    }

    public static void drawMarquee(GuiGraphicsExtractor graphics, Font font, String text, int color,
                                   int x, int y, int clipWidth, boolean shadow, long nowMs, long holdUntilMs,
                                   int speedPxPerSec) {
        drawMarquee(graphics, font, text, UiAssets.FONT_BODY, color, x, y, clipWidth, shadow, nowMs, holdUntilMs, speedPxPerSec);
    }

    public static void drawMarquee(GuiGraphicsExtractor graphics, Font font, String text, Identifier fontId, int color,
                                   int x, int y, int clipWidth, boolean shadow, long nowMs, long holdUntilMs,
                                   int speedPxPerSec) {
        String safe = text == null ? "" : text;
        if (safe.isEmpty() || clipWidth <= 0) return;
        drawMarquee(graphics, font, safe, fontId, color, x, y, clipWidth, shadow, nowMs, holdUntilMs,
            speedPxPerSec, UiText.width(font, safe, fontId, color));
    }

    public static void drawMarquee(GuiGraphicsExtractor graphics, Font font, String text, Identifier fontId, int color,
                                   int x, int y, int clipWidth, boolean shadow, long nowMs, long holdUntilMs,
                                   int speedPxPerSec, int textWidth) {
        String safe = text == null ? "" : text;
        if (safe.isEmpty() || clipWidth <= 0) return;
        if (textWidth <= clipWidth) {

            UiText.draw(graphics, font, safe, fontId, color, x, y, shadow);
            return;
        }

        float offset = offset(textWidth, clipWidth, nowMs, holdUntilMs, speedPxPerSec);
        UiScissorStack.global().push(graphics, UiBounds.of(x, y, clipWidth, font.lineHeight + 2));
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(-offset, 0.0f);
            UiText.draw(graphics, font, safe, fontId, color, x, y, shadow);
            UiText.draw(graphics, font, safe, fontId, color, x + cycleLength(textWidth), y, shadow);
        } finally {
            graphics.pose().popMatrix();
            UiScissorStack.global().pop(graphics);
        }
    }

    public static double interpolatePosition(double anchorSec, long anchorAtMs, boolean playing, double durationSec, long nowMs) {
        double position = anchorSec;
        if (playing && durationSec > 0.0) {
            position += (nowMs - anchorAtMs) / 1000.0;
        }
        return durationSec > 0.0 ? Math.max(0.0, Math.min(durationSec, position)) : Math.max(0.0, position);
    }

    public static final class CompositionKey {
        private final long stamp;
        private final String mode;
        private final Object font;
        private final int metricsGeneration;

        public CompositionKey(long stamp, String mode, Object font, int metricsGeneration) {
            this.stamp = stamp;
            this.mode = mode == null ? "" : mode;
            this.font = font;
            this.metricsGeneration = metricsGeneration;
        }

        public boolean matches(long stamp, String mode, Object font, int metricsGeneration) {
            return this.stamp == stamp
                && this.metricsGeneration == metricsGeneration
                && this.font == font
                && this.mode.equals(mode == null ? "" : mode);
        }
    }

    public static String trackText(AutismSpotify.Snapshot snapshot) {
        if (snapshot == null || snapshot.title() == null || snapshot.title().isBlank()) return "";
        String title = snapshot.title().trim();
        String artist = snapshot.artist() == null ? "" : snapshot.artist().trim();
        return artist.isEmpty() ? title : artist + " — " + title;
    }
}
