package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiColors;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiTextRenderer;

import java.util.List;

public final class Banner {
    private static final int PAD_X = 6;
    private static final int BODY_GAP = 4;
    private static final int BOTTOM_PAD = 6;

    private Banner() {
    }

    public static int height(UiContext context, boolean hasDetail) {
        int headerHeight = context.theme().spacing().topBarHeight;
        int lineHeight = context.theme().typography().lineHeight;
        return headerHeight + BODY_GAP + lineHeight + (hasDetail ? BODY_GAP + lineHeight : 0) + BOTTOM_PAD;
    }

    public static int height(UiContext context, int width, String message, String detail) {
        int maxTextWidth = Math.max(1, width - (PAD_X * 2));
        int lineHeight = context.theme().typography().lineHeight;
        List<String> messageLines = context.text().wrapFully(message, maxTextWidth);
        List<String> detailLines = detail == null || detail.isEmpty() ? List.of() : context.text().wrapFully(detail, maxTextWidth);
        return context.theme().spacing().topBarHeight
            + BODY_GAP
            + Math.max(1, messageLines.size()) * lineHeight
            + (detailLines.isEmpty() ? 0 : BODY_GAP + detailLines.size() * lineHeight)
            + BOTTOM_PAD;
    }

    public static void render(UiContext context, UiBounds requestedBounds, String title, String message, String detail) {
        UiColors colors = context.theme().colors();
        UiTextRenderer text = context.text();
        boolean hasDetail = detail != null && !detail.isEmpty();
        boolean dockedToTop = requestedBounds.y() <= 0;
        int margin = 4;
        int width = Math.max(1, Math.min(requestedBounds.width(), Math.max(1, context.screenWidth() - (margin * 2))));
        int height = height(context, width, message, detail);
        int x = Math.max(margin, Math.min(requestedBounds.x(), Math.max(margin, context.screenWidth() - margin - width)));
        int y = dockedToTop
            ? 0
            : Math.max(margin, Math.min(requestedBounds.y(), Math.max(margin, context.screenHeight() - margin - height)));
        UiBounds bounds = UiBounds.of(x, y, width, height);
        int headerHeight = context.theme().spacing().topBarHeight;

        UiRenderer.rect(context.graphics(), bounds, colors.window);
        UiRenderer.rect(context.graphics(), UiBounds.of(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, headerHeight - 2)), colors.header);
        if (!dockedToTop) {
            UiRenderer.horizontalEdge(context.graphics(), x, y, width, colors.accent);
        }
        UiRenderer.horizontalEdge(context.graphics(), x, y + height - 1, width, colors.borderSoft);
        UiRenderer.verticalEdge(context.graphics(), x, y + (dockedToTop ? 1 : 0), height - (dockedToTop ? 1 : 0), colors.borderSoft);
        UiRenderer.verticalEdge(context.graphics(), x + width - 1, y + (dockedToTop ? 1 : 0), height - (dockedToTop ? 1 : 0), colors.borderSoft);

        int maxTextWidth = Math.max(0, width - (PAD_X * 2));
        int titleY = text.centeredY(UiBounds.of(x, y, width, headerHeight));
        int messageY = y + headerHeight + BODY_GAP;
        text.drawFitted(context.graphics(), title, x + PAD_X, titleY, maxTextWidth, colors.text);
        List<String> messageLines = text.wrapFully(message, maxTextWidth);
        for (int i = 0; i < messageLines.size(); i++) {
            text.draw(context.graphics(), messageLines.get(i), x + PAD_X, messageY + i * context.theme().typography().lineHeight, colors.text);
        }
        if (hasDetail) {
            int detailY = messageY + messageLines.size() * context.theme().typography().lineHeight + BODY_GAP;
            List<String> detailLines = text.wrapFully(detail, maxTextWidth);
            for (int i = 0; i < detailLines.size(); i++) {
                text.draw(context.graphics(), detailLines.get(i), x + PAD_X, detailY + i * context.theme().typography().lineHeight, colors.muted);
            }
        }
    }
}
