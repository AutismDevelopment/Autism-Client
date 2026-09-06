package autismclient.gui.multi;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class MultiTooltip {
    private static final int MAX_WIDTH = 190;
    private static final int BG = 0xF0100010;
    private static final int BORDER = 0x505000A0;
    private static final int TEXT = 0xFFFFFFFF;

    private MultiTooltip() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, String text, int mouseX, int mouseY) {
        if (graphics == null || font == null || text == null || text.isBlank()) return;
        List<String> lines = wrap(font, text, MAX_WIDTH);
        if (lines.isEmpty()) return;
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        int height = lines.size() * 10 - 2;
        int screenWidth = Math.max(1, AutismUiScale.getVirtualScreenWidth());
        int screenHeight = Math.max(1, AutismUiScale.getVirtualScreenHeight());
        int x = mouseX + 10;
        int y = mouseY - 12;
        if (x + width + 4 > screenWidth) x = Math.max(4, mouseX - width - 12);
        if (y + height + 4 > screenHeight) y = Math.max(4, screenHeight - height - 4);
        if (y < 4) y = 4;
        graphics.nextStratum();

        UiRenderer.rect(graphics, UiBounds.of(x - 3, y - 3, width + 6, height + 6), BG);
        UiRenderer.rect(graphics, UiBounds.of(x - 3, y - 3, 2, height + 6),
            autismclient.util.AutismTheme.recolor(0xFFFF3B3B, autismclient.util.AutismTheme.Channel.ACCENT));
        int lineY = y;
        for (String line : lines) {
            graphics.text(font, Component.literal(line).getVisualOrderText(), x, lineY, TEXT, true);
            lineY += 10;
        }
    }

    public static boolean hovered(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}
