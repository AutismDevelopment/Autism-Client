package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AutismText {
    private static final Map<Identifier, FontDescription> FONT_CACHE = new ConcurrentHashMap<>();

    private AutismText() {
    }

    public enum Tone {
        TITLE,
        LABEL,
        BODY,
        MUTED,
        ACCENT
    }

    public static int colorFor(Tone tone) {
        return switch (tone) {
            case TITLE, LABEL, BODY -> AutismColors.textPrimary();
            case MUTED -> AutismColors.textMuted();
            case ACCENT -> AutismColors.accent();
        };
    }

    public static MutableComponent literal(String value, Tone tone) {
        return Component.literal(value == null ? "" : value).setStyle(styleFor(tone));
    }

    public static MutableComponent literal(String value, int color) {
        Identifier fontId = fontIdFor(Tone.BODY);
        return Component.literal(value == null ? "" : value).setStyle(Style.EMPTY.withFont(fontSource(fontId)).withColor(color));
    }

    public static String sanitizeUiLabel(String value) {
        if (value == null || value.isEmpty()) return "";
        return value
            .replace("\u2713", "X")
            .replace("\u2714", "X")
            .replace("\u2715", "X")
            .replace("\u2716", "X")
            .replace("\u2192", "->")
            .replace("\u2190", "<-")
            .replace("\u2014", "-")
            .replace("\u2013", "-")
            .replace("\u00b7", "-")
            .replace("\u25bc", "v")
            .replace("\u25be", "v")
            .replace("\u25b2", "^")
            .replace("\u25b4", "^")
            .replace("\u25cf", "X")
            .replace("\u25cb", "O")
            .replace("\u2261", "=")
            .replace("\u2605", "*")
            .replace("\u221e", "INF")
            .replace("\u26a0", "WARN")
            .replace("\u26a1", "BURST")
            .trim();
    }

    public static Style styleFor(Tone tone) {
        int color = colorFor(tone);
        return Style.EMPTY.withFont(fontSource(fontIdFor(tone))).withColor(color);
    }

    public static int width(Font renderer, String value, Tone tone) {
        return autismclient.gui.vanillaui.components.UiText.width(renderer, value, fontIdFor(tone), colorFor(tone));
    }

    public static String trimToWidth(Font renderer, String value, int maxWidth, Tone tone) {
        return autismclient.gui.vanillaui.components.UiText.trimToWidth(renderer, value, maxWidth, fontIdFor(tone), colorFor(tone));
    }

    public static void draw(GuiGraphicsExtractor context, Font renderer, String value, Tone tone, int x, int y, boolean shadow) {
        autismclient.gui.vanillaui.components.UiText.draw(context, renderer, value, fontIdFor(tone), colorFor(tone), x, y, shadow);
    }

    public static void draw(GuiGraphicsExtractor context, Font renderer, String value, int color, int x, int y, boolean shadow) {
        autismclient.gui.vanillaui.components.UiText.draw(context, renderer, value, UiAssets.FONT_BODY, color, x, y, shadow);
    }

    private static Identifier fontIdFor(Tone tone) {
        return switch (tone) {
            case TITLE -> UiAssets.FONT_TITLE;
            case LABEL -> UiAssets.FONT_LABEL;
            case BODY, MUTED, ACCENT -> UiAssets.FONT_BODY;
        };
    }

    private static FontDescription fontSource(Identifier fontId) {
        return FONT_CACHE.computeIfAbsent(fontId, FontDescription.Resource::new);
    }
}
