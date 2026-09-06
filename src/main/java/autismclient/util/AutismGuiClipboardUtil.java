package autismclient.util;

import net.minecraft.client.Minecraft;

public final class AutismGuiClipboardUtil {
    private static final Minecraft MC = Minecraft.getInstance();

    private AutismGuiClipboardUtil() {
    }

    public static void copyGuiTitleJson() {
        if (MC.gui.screen() == null || MC.keyboardHandler == null) {
            AutismNotifications.error("Copy failed: no screen.");
            return;
        }

        String title = MC.gui.screen().getTitle() == null ? "" : MC.gui.screen().getTitle().getString();
        MC.keyboardHandler.setClipboard(title);
        AutismNotifications.copied("GUI title copied.");
    }
}
