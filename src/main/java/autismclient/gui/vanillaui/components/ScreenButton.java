package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ScreenButton {
    private final UiBounds bounds;
    private final Component label;
    private final Runnable action;
    private final Button.Tone tone;
    private boolean active = true;

    public ScreenButton(int x, int y, int width, int height, Component label, Button.Tone tone, Runnable action) {
        this.bounds = UiBounds.of(x, y, width, height);
        this.label = label == null ? Component.empty() : label;
        this.tone = tone == null ? Button.Tone.NORMAL : tone;
        this.action = action;
    }

    public ScreenButton setActive(boolean active) {
        this.active = active;
        return this;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        Button.render(UiContexts.overlay(graphics, font, mouseX, mouseY), bounds, label.getString(), tone,
            active && bounds.contains(mouseX, mouseY), false);
    }

    public boolean click(double mouseX, double mouseY, int mouseButton) {
        if (!active || mouseButton != 0 || !bounds.contains((int) mouseX, (int) mouseY)) return false;
        if (action != null) action.run();
        return true;
    }
}
