package autismclient.gui.vanillaui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class UiTextRenderer {
    private static final int MAX_CACHE = 2048;
    private static final int VANILLA_FONT_HEIGHT = 9;
    private static final int OPTICAL_TEXT_NUDGE = 1;
    private final Font font;
    private final Map<String, Integer> widthCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > MAX_CACHE;
        }
    };
    private final Map<TrimKey, String> trimCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TrimKey, String> eldest) {
            return size() > MAX_CACHE;
        }
    };
    private final Map<WrapKey, List<String>> wrapCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<WrapKey, List<String>> eldest) {
            return size() > MAX_CACHE;
        }
    };

    public UiTextRenderer(Font font) {
        this.font = font;
    }

    public Font font() {
        return font;
    }

    public int width(String text) {
        String safe = text == null ? "" : text;
        Integer cached = widthCache.get(safe);
        if (cached != null) return cached;
        int width = font.width(safe);
        widthCache.put(safe, width);
        return width;
    }

    public String trim(String text, int maxWidth) {
        String safe = text == null ? "" : text;
        if (maxWidth <= 0) return "";
        if (width(safe) <= maxWidth) return safe;
        TrimKey key = new TrimKey(safe, maxWidth);
        String cached = trimCache.get(key);
        if (cached != null) return cached;
        String trimmed = font.plainSubstrByWidth(safe, maxWidth);
        trimCache.put(key, trimmed);
        return trimmed;
    }

    public String trimEllipsis(String text, int maxWidth) {
        String safe = text == null ? "" : text;
        if (maxWidth <= 0 || safe.isEmpty()) return "";
        if (width(safe) <= maxWidth) return safe;
        String ellipsis = "...";
        int ellipsisWidth = width(ellipsis);
        if (maxWidth <= ellipsisWidth) return trim(safe, maxWidth);
        String base = font.plainSubstrByWidth(safe, Math.max(0, maxWidth - ellipsisWidth));
        while (!base.isEmpty() && width(base + ellipsis) > maxWidth) {
            base = base.substring(0, base.length() - 1);
        }
        return base.isEmpty() ? ellipsis : base + ellipsis;
    }

    public void draw(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(font, text == null ? "" : text, x, y, color, false);
    }

    public void drawTrimmed(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        draw(graphics, trim(text, maxWidth), x, y, color);
    }

    public void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        drawFitted(graphics, text, x, y, maxWidth, color, 0.75f);
    }

    public void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color, float minimumScale) {
        String safe = text == null ? "" : text;
        if (safe.isEmpty() || maxWidth <= 0) return;
        int textWidth = width(safe);
        if (textWidth <= maxWidth) {
            draw(graphics, safe, x, y, color);
            return;
        }

        float requiredScale = Math.min(1.0f, maxWidth / (float) Math.max(1, textWidth));
        float scale = requiredScale;

        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(scale, scale);
            draw(graphics, safe, Math.round(x / scale), Math.round(y / scale), color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public void drawEllipsized(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        String display = trimEllipsis(text, maxWidth);
        if (display.isEmpty()) return;
        draw(graphics, display, x, y, color);
    }

    public List<String> wrapFully(String text, int maxWidth) {
        String safe = text == null ? "" : text;
        int width = Math.max(1, maxWidth);
        WrapKey key = new WrapKey(safe, width);
        List<String> cached = wrapCache.get(key);
        if (cached != null) return cached;
        List<String> lines = new ArrayList<>();
        for (TextWrapLayout.Line line : TextWrapLayout.layout(safe, width,
            (start, end) -> this.width(safe.substring(start, end)))) {
            lines.add(safe.substring(line.start(), line.renderEnd()));
        }
        if (lines.isEmpty()) lines.add("");
        List<String> result = List.copyOf(lines);
        wrapCache.put(key, result);
        return result;
    }

    public void drawCentered(GuiGraphicsExtractor graphics, String text, UiBounds bounds, int color) {
        drawCentered(graphics, text, bounds, color, 0);
    }

    public void drawCentered(GuiGraphicsExtractor graphics, String text, UiBounds bounds, int color, int horizontalNudge) {
        String display = text == null ? "" : text;
        int maxWidth = Math.max(0, bounds.width() - 4);
        float scale = width(display) <= maxWidth ? 1.0f : Math.min(1.0f, maxWidth / (float) Math.max(1, width(display)));
        int displayWidth = Math.round(width(display) * scale);
        int centeredX = bounds.x() + Math.max(2, (bounds.width() - displayWidth + 1) / 2);
        int minX = bounds.x() + 1;
        int maxX = Math.max(minX, bounds.right() - displayWidth - 1);
        int x = Math.max(minX, Math.min(maxX, centeredX + horizontalNudge));
        int y = centeredY(bounds);
        drawFitted(graphics, display, x, y, maxWidth, color);
    }

    public int centeredY(UiBounds bounds) {
        int spareHeight = Math.max(0, bounds.height() - VANILLA_FONT_HEIGHT);
        return bounds.y() + Math.max(1, (spareHeight + 1) / 2 + OPTICAL_TEXT_NUDGE);
    }

    private record TrimKey(String text, int maxWidth) {
        private TrimKey {
            Objects.requireNonNull(text);
        }
    }

    private record WrapKey(String text, int maxWidth) {
        private WrapKey {
            Objects.requireNonNull(text);
        }
    }
}
