package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class TextField {
    private TextField() {
    }

    public static void render(UiContext context, UiBounds bounds, String text, String placeholder, boolean focused, int cursor) {
        render(context, bounds, text, placeholder, focused, cursor, -1, -1);
    }

    public static void render(UiContext context, UiBounds bounds, String text, String placeholder, boolean focused, int cursor, int selectionStart, int selectionEnd) {
        var colors = context.theme().colors();
        UiRenderer.frame(context.graphics(), bounds, focused ? colors.fieldFocused : colors.field, focused ? colors.accent : colors.borderSoft);
        String value = text == null ? "" : text;
        String shown = value.isEmpty() && !focused ? placeholder : value;
        int textX = bounds.x() + 4;
        int textY = context.text().centeredY(bounds);
        if (focused && selectionStart >= 0 && selectionEnd > selectionStart) {
            int start = Math.max(0, Math.min(selectionStart, value.length()));
            int end = Math.max(0, Math.min(selectionEnd, value.length()));
            int selectX = textX + context.text().width(value.substring(0, start));
            int selectW = Math.max(1, context.text().width(value.substring(start, end)));
            UiRenderer.rect(context.graphics(), UiBounds.of(selectX, bounds.y() + 3, Math.min(selectW, bounds.right() - 3 - selectX), Math.max(1, bounds.height() - 6)), 0x663355FF);
        }
        context.text().drawTrimmed(context.graphics(), shown, textX, textY, Math.max(1, bounds.width() - 8), value.isEmpty() && !focused ? colors.muted : colors.text);
        if (focused) {
            String before = value.substring(0, Math.max(0, Math.min(cursor, value.length())));
            int caret = Math.min(bounds.right() - 3, textX + context.text().width(before));
            UiRenderer.rect(context.graphics(), UiBounds.of(caret, bounds.y() + 3, 1, Math.max(1, bounds.height() - 6)), colors.text);
        }
    }
}
