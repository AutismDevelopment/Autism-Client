package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.Scrollbar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompactScrollbar {

    public static final int GUTTER = 6;

    public record Metrics(int trackX, int trackY, int trackWidth, int trackHeight, int thumbY, int thumbHeight, int maxScroll) {
        public boolean hasScroll() {
            return maxScroll > 0;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= trackX && mouseX < trackX + trackWidth && mouseY >= trackY && mouseY < trackY + trackHeight;
        }

        public boolean overThumb(double mouseX, double mouseY) {
            return hasScroll()
                && mouseX >= trackX && mouseX < trackX + trackWidth
                && mouseY >= thumbY && mouseY < thumbY + thumbHeight;
        }
    }

    private CompactScrollbar() {
    }

    public static Metrics compute(int contentPixels, int viewPixels, int trackX, int trackY, int trackWidth, int trackHeight, int scrollOffset) {
        return compute(contentPixels, viewPixels, trackX, trackY, trackWidth, trackHeight, (float) scrollOffset);
    }

    public static Metrics compute(int contentPixels, int viewPixels, int trackX, int trackY, int trackWidth, int trackHeight, float scrollOffset) {

        int right = trackX + trackWidth;
        trackWidth = GUTTER;
        trackX = right - trackWidth;
        if (contentPixels <= 0 || viewPixels <= 0 || trackHeight <= 0) {
            return new Metrics(trackX, trackY, trackWidth, trackHeight, trackY, trackHeight, 0);
        }

        int maxScroll = Math.max(0, contentPixels - viewPixels);
        if (maxScroll <= 0) {
            return new Metrics(trackX, trackY, trackWidth, trackHeight, trackY, trackHeight, 0);
        }

        int thumbHeight = Math.max(12, (int) Math.round(trackHeight * (viewPixels / (double) Math.max(viewPixels, contentPixels))));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        float clampedScroll = Math.max(0.0f, Math.min(scrollOffset, maxScroll));
        int thumbY = trackY + Math.round((clampedScroll / maxScroll) * travel);
        return new Metrics(trackX, trackY, trackWidth, trackHeight, thumbY, thumbHeight, maxScroll);
    }

    public static void draw(GuiGraphicsExtractor context, Metrics metrics, boolean hovered, boolean dragging) {
        if (metrics == null || !metrics.hasScroll()) return;

        Scrollbar.render(
            UiContexts.overlay(context, Minecraft.getInstance().font, -1, -1),
            new Scrollbar.Metrics(
                UiBounds.of(metrics.trackX(), metrics.trackY(), metrics.trackWidth(), metrics.trackHeight()),
                UiBounds.of(metrics.trackX(), metrics.thumbY(), metrics.trackWidth(), metrics.thumbHeight()),
                metrics.maxScroll()
            ),
            hovered,
            dragging
        );
    }

    public static int scrollFromThumb(Metrics metrics, double mouseY, int grabOffset) {
        if (metrics == null || !metrics.hasScroll()) return 0;
        int travel = Math.max(1, metrics.trackHeight() - metrics.thumbHeight());
        int thumbTop = (int) Math.round(mouseY) - grabOffset;
        thumbTop = Math.max(metrics.trackY(), Math.min(metrics.trackY() + travel, thumbTop));
        float progress = (float) (thumbTop - metrics.trackY()) / travel;
        return Math.max(0, Math.min(metrics.maxScroll(), Math.round(progress * metrics.maxScroll())));
    }
}
