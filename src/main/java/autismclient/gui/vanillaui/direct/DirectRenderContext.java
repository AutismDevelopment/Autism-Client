package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class DirectRenderContext {
    private final GuiGraphicsExtractor drawContext;
    private final Font textRenderer;
    private final DirectViewport viewport;
    private final CompactTheme theme;
    private final float mouseX;
    private final float mouseY;
    private final float delta;
    private final float alpha;

    public DirectRenderContext(GuiGraphicsExtractor drawContext, Font textRenderer, DirectViewport viewport, CompactTheme theme, float mouseX, float mouseY, float delta) {
        this(drawContext, textRenderer, viewport, theme, mouseX, mouseY, delta, 1.0f);
    }

    public DirectRenderContext(GuiGraphicsExtractor drawContext, Font textRenderer, DirectViewport viewport, CompactTheme theme, float mouseX, float mouseY, float delta, float alpha) {
        this.drawContext = drawContext;
        this.textRenderer = textRenderer;
        this.viewport = viewport;
        this.theme = theme;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.delta = delta;
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    public GuiGraphicsExtractor drawContext() {
        return drawContext;
    }

    public Font textRenderer() {
        return textRenderer;
    }

    public DirectViewport viewport() {
        return viewport;
    }

    public CompactTheme theme() {
        return theme;
    }

    public float mouseX() {
        return mouseX;
    }

    public float mouseY() {
        return mouseY;
    }

    public float delta() {
        return delta;
    }

    public float alpha() {
        return alpha;
    }

    public DirectRenderContext withAlpha(float alpha) {
        return new DirectRenderContext(drawContext, textRenderer, viewport, theme, mouseX, mouseY, delta, alpha);
    }

    public int applyAlpha(int color) {
        return applyAlpha(color, alpha);
    }

    public void drawTexturedQuad(Identifier textureId, int x1, int y1, int x2, int y2) {
        if (drawContext == null || textureId == null) return;
        drawContext.blit(
            textureId,
            x1,
            y1,
            x2,
            y2,
            0.0f,
            1.0f,
            0.0f,
            1.0f
        );
        if (alpha < 0.999f) {
            int overlayAlpha = Math.max(0, Math.min(255, Math.round((1.0f - alpha) * 255.0f)));
            drawContext.fill(x1, y1, x2, y2, (overlayAlpha << 24) | 0x00070709);
        }
    }

    public static int applyAlpha(int color, float alpha) {
        int a = (color >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, Math.round(a * Math.max(0.0f, Math.min(1.0f, alpha)))));
        return (scaled << 24) | (color & 0x00FFFFFF);
    }
}
