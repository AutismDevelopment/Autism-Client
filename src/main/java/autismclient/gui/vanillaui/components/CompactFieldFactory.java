package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;

public final class CompactFieldFactory {
    private CompactFieldFactory() {
    }

    public static void text(UiContext context, UiBounds bounds, String text, String placeholder, boolean focused, int cursor) {
        TextField.render(context, bounds, text, placeholder, focused, cursor);
    }

    public static void text(UiContext context, UiBounds bounds, String text, String placeholder, boolean focused, int cursor, int selectionStart, int selectionEnd) {
        TextField.render(context, bounds, text, placeholder, focused, cursor, selectionStart, selectionEnd);
    }

    public static void toggle(UiContext context, UiBounds bounds, boolean enabled, boolean hovered) {
        Toggle.render(context, bounds, enabled, hovered);
    }

    public static void dropdown(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open) {
        Dropdown.renderControl(context, bounds, label, hovered, open);
    }

    public static void dropdownCentered(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open) {
        Dropdown.renderControlCentered(context, bounds, label, hovered, open);
    }
}
