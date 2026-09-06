package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

import java.util.List;

public final class Tooltip {
    private static final int SCREEN_MARGIN = 4;
    private static final int PADDING = 4;
    private static final int ANCHOR_GAP = 8;
    private static final int LINE_HEIGHT = 10;
    private Tooltip() {
    }

    public static void render(UiContext context, String text, int mouseX, int mouseY) {
        render(context, text, mouseX, mouseY, 220);
    }

    public static void render(UiContext context, String text, int mouseX, int mouseY, int preferredMaxWidth) {
        if (text == null || text.isBlank()) return;

        int availableW = Math.max(1, context.screenWidth() - SCREEN_MARGIN * 2);
        int availableH = Math.max(1, context.screenHeight() - SCREEN_MARGIN * 2);
        int maxTextWidth = Math.max(1, Math.min(Math.max(32, preferredMaxWidth), Math.max(1, availableW - PADDING * 2)));
        List<String> lines = context.text().wrapFully(text, maxTextWidth);
        int maxLineWidth = 0;
        for (String line : lines) maxLineWidth = Math.max(maxLineWidth, context.text().width(line));

        int w = Math.min(availableW, Math.max(1, maxLineWidth + PADDING * 2));
        int maxLines = Math.max(1, Math.min(lines.size(), Math.max(1, (availableH - PADDING * 2) / LINE_HEIGHT)));
        int h = Math.min(availableH, Math.max(1, maxLines * LINE_HEIGHT + PADDING * 2));
        int x = mouseX + ANCHOR_GAP;
        int y = mouseY + ANCHOR_GAP;
        if (x + w > context.screenWidth() - SCREEN_MARGIN) x = mouseX - w - ANCHOR_GAP;
        if (y + h > context.screenHeight() - SCREEN_MARGIN) y = mouseY - h - ANCHOR_GAP;
        UiBounds bounds = UiBounds.of(
            clamp(x, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, context.screenWidth() - SCREEN_MARGIN - w)),
            clamp(y, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, context.screenHeight() - SCREEN_MARGIN - h)),
            w,
            h
        );

        UiRenderer.rect(context.graphics(), bounds, context.theme().colors().windowStrong);
        UiRenderer.rect(context.graphics(), UiBounds.of(bounds.x(), bounds.y(), 2, bounds.height()),
            context.theme().colors().accent);
        for (int i = 0; i < maxLines; i++) {
            context.text().draw(context.graphics(), lines.get(i), bounds.x() + PADDING + 2, bounds.y() + PADDING + i * LINE_HEIGHT,
                context.theme().colors().text);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

}
