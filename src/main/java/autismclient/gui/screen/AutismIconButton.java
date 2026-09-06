package autismclient.gui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class AutismIconButton extends Button {
    private final Identifier icon;
    private final int textureWidth;
    private final int textureHeight;
    private final int iconSize;

    public AutismIconButton(int x, int y, int width, int height, Component label, Identifier icon,
                            int textureSize, int iconSize, OnPress onPress) {
        this(x, y, width, height, label, icon, textureSize, textureSize, iconSize, onPress);
    }

    public AutismIconButton(int x, int y, int width, int height, Component label, Identifier icon,
                            int textureWidth, int textureHeight, int iconSize, OnPress onPress) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.textureWidth = Math.max(1, textureWidth);
        this.textureHeight = Math.max(1, textureHeight);
        this.iconSize = Math.max(1, iconSize);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int color = active ? 0xFFFFFFFF : 0xFF8A7474;
        if (icon == null) {
            int textW = font.width(getMessage());
            graphics.text(font, getMessage(), getX() + (getWidth() - textW) / 2,
                textY(font), color);
            return;
        }
        int drawSize = Math.min(iconSize, Math.max(1, Math.min(getWidth() - 4, getHeight() - 4)));
        boolean hasRoomForText = getWidth() >= drawSize + font.width(getMessage()) + 18;
        int iconX = hasRoomForText ? getX() + 3 : getX() + (getWidth() - drawSize) / 2;
        int iconY = getY() + (getHeight() - drawSize) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0.0F, 0.0F, drawSize, drawSize,
            textureWidth, textureHeight, textureWidth, textureHeight, color);
        if (hasRoomForText) {
            int textX = iconX + drawSize + 4;
            graphics.text(font, getMessage(), textX, textY(font), color);
        }
    }

    private int textY(Font font) {
        int spareHeight = Math.max(0, getHeight() - font.lineHeight);
        return getY() + ((spareHeight + 1) / 2) + 1;
    }
}
