package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.SectionPanel;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

public abstract class AutismScreen extends Screen {
    protected AutismScreen(Component title) {
        super(title);
    }

    protected static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    protected static MouseButtonEvent virtualEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(AutismUiScale.toVirtual(event.x()), AutismUiScale.toVirtual(event.y()), new MouseButtonInfo(event.button(), event.modifiers()));
    }

    protected int screenWidth() {
        int width = AutismUiScale.getVirtualScreenWidth();
        return width <= 0 ? this.width : width;
    }

    protected int screenHeight() {
        int height = AutismUiScale.getVirtualScreenHeight();
        return height <= 0 ? this.height : height;
    }

    protected void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill) {
        SectionPanel.renderBody(UiContexts.overlay(graphics, font, -10000, -10000), UiBounds.of(x, y, w, h), fill);
    }

    protected void toast(String message, int accentColor) {
        if (message == null || message.isBlank() || this.minecraft == null) return;
        this.minecraft.execute(() -> AutismNotifications.show(message, accentColor));
    }
}
