package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class AutismStyledButton extends net.minecraft.client.gui.components.Button {
    private static final int MASK = 0xFF120B0F;

    private final Button.Tone tone;
    private final java.util.function.Supplier<String> dynamicLabel;

    public AutismStyledButton(int x, int y, int width, int height, Component label, Button.Tone tone, OnPress onPress) {
        this(x, y, width, height, label, tone, null, onPress);
    }

    public AutismStyledButton(int x, int y, int width, int height, Component label, Button.Tone tone,
                              java.util.function.Supplier<String> dynamicLabel, OnPress onPress) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.tone = tone == null ? Button.Tone.NORMAL : tone;
        this.dynamicLabel = dynamicLabel;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        UiBounds bounds = UiBounds.of(getX(), getY(), getWidth(), getHeight());
        UiRenderer.rect(graphics, bounds, AutismTheme.recolor(MASK, Channel.BUTTON));
        boolean hovered = this.active
            && mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= getY() && mouseY < getY() + getHeight();
        String text = dynamicLabel != null ? dynamicLabel.get() : getMessage().getString();
        Button.render(
            UiContexts.overlay(graphics, font, mouseX, mouseY),
            bounds,
            text,
            this.active ? tone : Button.Tone.NORMAL,
            hovered,
            false
        );
    }
}
