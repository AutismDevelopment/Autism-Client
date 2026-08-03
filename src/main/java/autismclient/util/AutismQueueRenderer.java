package autismclient.util;

import autismclient.gui.vanillaui.direct.DirectHudPanelRenderer;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class AutismQueueRenderer {
    private static final CompactTheme THEME = new CompactTheme();
    private static final long CACHE_REFRESH_NANOS = 50_000_000L;
    private static final int ENTRY_COLOR = 0xFFE7DCDD;
    private static final int ENTRY_SENDING_COLOR = 0xFFF6D28D;
    private static final int STATUS_COLOR = 0xFF9EDAB0;
    private static final int ACTIVE_ACCENT_COLOR = 0xFF73D98E;
    private static final Identifier MUTED_FONT = THEME.fontFor(UiTone.MUTED);
    private static final Identifier BODY_FONT = THEME.fontFor(UiTone.BODY);
    private static final Identifier LABEL_FONT = THEME.fontFor(UiTone.LABEL);
    private static final int MUTED_COLOR = THEME.color(UiTone.MUTED);
    private static final int HEADER_COLOR = THEME.color(UiTone.BODY);

    private static boolean cachedSending;
    private static String cachedModeStr = null;
    private static int cachedMaxLines = Integer.MIN_VALUE;
    private static int cachedPanelWidth = Integer.MIN_VALUE;
    private static int cachedPacketCount = Integer.MIN_VALUE;
    private static int cachedQueueRevision = Integer.MIN_VALUE;
    private static String cachedTitle = "";
    private static int cachedAccentColor;
    private static List<DirectHudPanelRenderer.Row> cachedRows = List.of();
    private static long lastCacheBuildNanos;

    private AutismQueueRenderer() {}

    public static void render(GuiGraphicsExtractor context, Font textRenderer, int x, int y, int width, int maxLines) {
        renderStacked(context, textRenderer, x - 6, y - 8, width + 12, maxLines, true, true, true);
    }

    public static int measureStacked(Font textRenderer, int panelWidth, int maxLines) {
        if (textRenderer == null) return 0;
        ensureCache(textRenderer, panelWidth, maxLines);
        return DirectHudPanelRenderer.panelHeight(cachedRows.size());
    }

    public static int renderStacked(GuiGraphicsExtractor context, Font textRenderer, int x, int y, int panelWidth, int maxLines,
                                    boolean topBorder, boolean bottomBorder, boolean rightBorder) {
        if (textRenderer == null) return 0;

        ensureCache(textRenderer, panelWidth, maxLines);
        return DirectHudPanelRenderer.renderPreTrimmed(context, textRenderer, x, y, panelWidth, cachedTitle, cachedRows, cachedAccentColor,
            0, true, rightBorder, topBorder, bottomBorder);
    }

    private static void ensureCache(Font textRenderer, int panelWidth, int maxLines) {
        AutismSharedState shared = AutismSharedState.get();

        boolean isSending = shared.hasStaggeredPackets();
        String modeStr = shared.getQueueDisplayDelayMode() == AutismSharedState.DelayMode.MS ? "ms" : "t";
        int queueRevision = shared.getQueueRenderRevision();
        long now = System.nanoTime();
        if (shouldRebuildCache(isSending, modeStr, maxLines, panelWidth, queueRevision, now)) {
            rebuildCache(textRenderer, shared.getQueueRenderSnapshot(isSending, maxLines), isSending, modeStr, maxLines, panelWidth, queueRevision, now);
        }
    }

    private static boolean shouldRebuildCache(boolean isSending, String modeStr, int maxLines, int panelWidth, int queueRevision, long now) {
        boolean layoutChanged = cachedSending != isSending
            || !java.util.Objects.equals(cachedModeStr, modeStr)
            || cachedMaxLines != maxLines
            || cachedPanelWidth != panelWidth;
        if (layoutChanged) {
            return true;
        }
        if (cachedRows.isEmpty()) {
            return true;
        }
        if (cachedQueueRevision == queueRevision) {
            return false;
        }
        return now - lastCacheBuildNanos >= CACHE_REFRESH_NANOS;
    }

    private static void rebuildCache(Font textRenderer, AutismSharedState.QueueRenderSnapshot snapshot, boolean isSending, String modeStr, int maxLines, int panelWidth, int queueRevision, long now) {
        List<AutismSharedState.QueuedPacket> packets = snapshot.packets();
        int totalCount = snapshot.totalCount();
        int visibleCount = Math.min(packets.size(), maxLines);
        int contentWidth = panelWidth - (6 * 2);
        ArrayList<DirectHudPanelRenderer.Row> rows = new ArrayList<>(visibleCount + 2);
        rows.add(new DirectHudPanelRenderer.Row(
            UiText.trimToWidth(textRenderer, isSending ? "Sending " + totalCount + " left [" + modeStr + "]" : totalCount + " queued [" + modeStr + "]", contentWidth, BODY_FONT, STATUS_COLOR),
            BODY_FONT,
            STATUS_COLOR
        ));

        if (packets.isEmpty()) {
            rows.add(new DirectHudPanelRenderer.Row(
                UiText.trimToWidth(textRenderer, "Queue empty", contentWidth, MUTED_FONT, MUTED_COLOR),
                MUTED_FONT,
                MUTED_COLOR
            ));
        } else {
            for (int i = 0; i < visibleCount; i++) {
                AutismSharedState.QueuedPacket qp = packets.get(i);
                String simpleName = AutismPacketNamer.getFriendlyName(qp.packet);
                String delayStr = qp.getDelay() > 0 ? " +" + qp.getDelay() + modeStr : "";
                String label = "#" + qp.getId() + " " + (qp.isExactReplay() ? "[Exact] " : "") + simpleName + delayStr;
                int color = (isSending && i == 0) ? ENTRY_SENDING_COLOR : ENTRY_COLOR;
                rows.add(new DirectHudPanelRenderer.Row(
                    UiText.trimToWidth(textRenderer, label, contentWidth, BODY_FONT, color),
                    BODY_FONT,
                    color
                ));
            }
        }

        cachedSending = isSending;
        cachedModeStr = modeStr;
        cachedMaxLines = maxLines;
        cachedPanelWidth = panelWidth;
        cachedPacketCount = totalCount;
        cachedQueueRevision = queueRevision;
        cachedTitle = UiText.trimToWidth(textRenderer, "PACKET QUEUE", contentWidth, LABEL_FONT, HEADER_COLOR);
        cachedAccentColor = isSending ? ACTIVE_ACCENT_COLOR : THEME.headerAccent();
        cachedRows = rows;
        lastCacheBuildNanos = now;
    }
}
