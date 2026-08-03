package autismclient.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class AutismUiIcons {
    public static final Identifier X = icon("x");
    public static final Identifier DOT = icon("dot");
    public static final Identifier CHECK = icon("check");
    public static final Identifier CHEVRON_DOWN = icon("chevron_down");
    public static final Identifier CHEVRON_UP = icon("chevron_up");
    public static final Identifier CHEVRON_RIGHT = icon("chevron_right");
    public static final Identifier PILL = icon("pill_track");
    public static final Identifier PLAY = icon("play");
    public static final Identifier STOP = icon("stop");
    public static final Identifier TRASH = icon("trash");
    public static final Identifier EDIT = icon("edit");
    public static final Identifier DUPLICATE = icon("duplicate");
    public static final Identifier PLUS = icon("plus");

    private static final int ICON_TEX = 512;
    private static final int PILL_TEX_W = 2048;
    private static final int PILL_TEX_H = 1024;

    private AutismUiIcons() {}

    private static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("autismclient", "textures/gui/icons/" + name + ".png");
    }

    public static void blit(GuiGraphicsExtractor graphics, Identifier icon, int x, int y, int size, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F, size, size, ICON_TEX, ICON_TEX, ICON_TEX, ICON_TEX, color);
    }

    public static void blit(GuiGraphicsExtractor graphics, Identifier icon, int x, int y, int w, int h, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F, w, h, ICON_TEX, ICON_TEX, ICON_TEX, ICON_TEX, color);
    }

    public static void blitSliced(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int cap = Math.min(h / 2, Math.max(1, w / 2));
        int srcCap = PILL_TEX_H / 2;
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;

        graphics.blit(pipeline, PILL, x, y, 0.0F, 0.0F, cap, h, srcCap, PILL_TEX_H, PILL_TEX_W, PILL_TEX_H, color);

        if (w - 2 * cap > 0) {
            graphics.blit(pipeline, PILL, x + cap, y, (float) srcCap, 0.0F, w - 2 * cap, h,
                PILL_TEX_W - 2 * srcCap, PILL_TEX_H, PILL_TEX_W, PILL_TEX_H, color);
        }

        graphics.blit(pipeline, PILL, x + w - cap, y, (float) (PILL_TEX_W - srcCap), 0.0F, cap, h,
            srcCap, PILL_TEX_H, PILL_TEX_W, PILL_TEX_H, color);
    }
}
