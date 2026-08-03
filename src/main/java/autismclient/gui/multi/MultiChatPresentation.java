package autismclient.gui.multi;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismClientMessaging;
import autismclient.util.multi.MultiManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MultiChatPresentation {
    public record VisualRow(MultiManager.ChatLine line, int lineIndex,
                            FormattedCharSequence render, FormattedText hit) {
    }

    private MultiChatPresentation() {
    }

    public static List<VisualRow> wrap(Font font, List<MultiManager.ChatLine> lines, int width, int totalAccounts,
                                       Function<String, String> accountLabel, int mutedColor) {
        List<VisualRow> out = new ArrayList<>();
        if (font == null || lines == null) return out;
        int available = Math.max(1, width);
        for (MultiManager.ChatLine line : lines) {
            Component full = prefixed(line, totalAccounts, accountLabel, mutedColor);
            List<FormattedCharSequence> render = font.split(full, available);
            List<FormattedText> hit = font.getSplitter().splitLines(full, available, Style.EMPTY);
            for (int index = 0; index < render.size(); index++) {
                FormattedText hitLine = index < hit.size() ? hit.get(index) : FormattedText.EMPTY;
                out.add(new VisualRow(line, index, render.get(index), hitLine));
            }
        }
        return out;
    }

    public static Component prefixed(MultiManager.ChatLine line, int totalAccounts,
                                     Function<String, String> accountLabel, int mutedColor) {
        Component message = line == null || line.render() == null ? Component.empty() : line.render();
        return prefixed(line, message, totalAccounts, accountLabel, mutedColor);
    }

    public static Component recipientPrefix(MultiManager.ChatLine line, int totalAccounts,
                                            Function<String, String> accountLabel, int mutedColor) {
        if (line == null || line.system()) return null;
        Map<String, Component> targets = line.targets();
        int count = targets == null ? 0 : targets.size();

        if (count != 1 || totalAccounts <= 1) return null;
        String id = targets.keySet().iterator().next();
        String label = accountLabel == null ? null : accountLabel.apply(id);
        String name = label == null || label.isBlank() ? id : MultiManager.singleLine(label, 16);
        return Component.literal("[" + name + "] ").withColor(mutedColor);
    }

    public static void underlineClickableLine(GuiGraphicsExtractor graphics, Font font,
                                              FormattedText line, int x, int y) {
        if (graphics == null || font == null || line == null) return;
        int[] offset = {0};
        line.visit((style, text) -> {
            int width = font.width(FormattedText.of(text, style));
            if (style.getClickEvent() != null && width > 0) {
                UiRenderer.rect(graphics, UiBounds.of(x + offset[0], y + 9, width, 1), 0xFF7AA7FF);
            }
            offset[0] += width;
            return java.util.Optional.empty();
        }, Style.EMPTY);
    }

    public static void openLinkSafely(java.net.URI uri) {
        if (uri == null || uri.getScheme() == null) return;
        String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (scheme.equals("http") || scheme.equals("https")) {
            net.minecraft.util.Util.getPlatform().openUri(uri);
        }
    }

    public static ClickEvent resolveClick(Font font, FormattedText line, int relativeX) {
        if (font == null || line == null || relativeX < 0) return null;
        ClickEvent[] found = {null};
        int[] offset = {0};
        line.visit((style, text) -> {
            int width = font.width(FormattedText.of(text, style));
            if (relativeX < offset[0] + width) {
                found[0] = style.getClickEvent();
                return java.util.Optional.of(Boolean.TRUE);
            }
            offset[0] += width;
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    public static int runCommands(Font font, MultiManager.ChatLine line, int lineIndex, int relativeX,
                                  int wrapWidth, int totalAccounts, Function<String, String> accountLabel,
                                  int mutedColor, ClickEvent.RunCommand clicked) {
        if (font == null || line == null || line.targets() == null) return 0;
        int ran = 0;
        for (Map.Entry<String, Component> target : line.targets().entrySet()) {
            Component full = prefixed(line, target.getValue(), totalAccounts, accountLabel, mutedColor);
            List<FormattedText> wrapped = font.getSplitter().splitLines(full, Math.max(1, wrapWidth), Style.EMPTY);
            ClickEvent resolved = lineIndex < wrapped.size()
                ? resolveClick(font, wrapped.get(lineIndex), relativeX) : null;
            String command = resolved instanceof ClickEvent.RunCommand own ? own.command()
                : clicked == null ? null : clicked.command();
            if (command == null || command.isBlank()) continue;
            MultiManager.get().sendCommandTo(target.getKey(), command);
            ran++;
        }
        return ran;
    }

    private static Component prefixed(MultiManager.ChatLine line, Component message, int totalAccounts,
                                      Function<String, String> accountLabel, int mutedColor) {
        var out = Component.empty();
        if (line != null && line.source() != null && !line.source().isEmpty()) {
            out.append(AutismClientMessaging.themedTag(MultiManager.singleLine(line.source(), 24)));
        }
        Component recipients = recipientPrefix(line, totalAccounts, accountLabel, mutedColor);
        if (recipients != null) out.append(recipients);

        out.append(message == null ? Component.empty() : MultiChatLinks.linkify(message));
        if (line != null && line.count() > 1) {
            out.append(Component.literal(" (x" + line.count() + ")").withColor(mutedColor));
        }
        return out;
    }
}
