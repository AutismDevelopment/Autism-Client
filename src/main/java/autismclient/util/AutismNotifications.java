package autismclient.util;

import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.ToastStack;
import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class AutismNotifications {
    private static final int SUCCESS = 0xFF35D873;
    private static final int WARNING = 0xFFFFC857;
    private static final int ERROR = 0xFFFF5B5B;
    private static final ToastStack TOASTS = new ToastStack(2100L, 140.0f, 220.0f, 4, 4, 18);

    private AutismNotifications() {
    }

    public static void copied(String message) {
        show(message == null || message.isBlank() ? "Copied." : message, SUCCESS);
    }

    public static void success(String message) {
        show(message, SUCCESS);
    }

    public static void warning(String message) {
        show(message, WARNING);
    }

    public static void error(String message) {
        show(message, ERROR);
    }

    public static void show(String message, int accentColor) {
        if (PackHideState.shouldSuppressClientOutput()) return;
        TOASTS.show(message, accentColor);
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (PackHideState.shouldSuppressClientOutput()) {
            clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (graphics == null || mc == null || mc.font == null) return;
        AutismNameScrape.render(graphics);
        if (!TOASTS.hasVisibleToasts()) return;
        int width = Math.min(320, Math.max(120, AutismUiScale.getVirtualScreenWidth() - 16));
        int x = Math.max(8, (AutismUiScale.getVirtualScreenWidth() - width) / 2);
        TOASTS.render(UiContexts.overlay(graphics, mc.font, -1, -1), x, 8, width);
    }

    public static boolean hasVisible() {
        if (PackHideState.shouldSuppressClientOutput()) return false;

        return TOASTS.hasVisibleToasts() || AutismNameScrape.isActive();
    }

    public static int pendingCount() {
        return TOASTS.size();
    }

    public static void clear() {
        TOASTS.clear();
    }
}
