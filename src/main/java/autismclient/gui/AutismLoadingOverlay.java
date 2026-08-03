package autismclient.gui;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismColors;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismThemeTextures;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.function.Consumer;

public class AutismLoadingOverlay extends LoadingOverlay {
    private static final Identifier CUSTOM_LOGO =
        Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/loading_logo.png");
    private static final int LOGO_WIDTH = 2106;
    private static final int LOGO_HEIGHT = 1297;
    private static final int BG_COLOR = AutismColors.loadingBg();
    private static final int BAR_R = 236;
    private static final int BAR_G = 32;
    private static final int BAR_B = 39;

    private static final long FADE_OUT_TIME = 1000L;
    private static final long FADE_IN_TIME = 500L;

    private final Minecraft autism$minecraft;
    private final ReloadInstance autism$reload;
    private final Consumer<Optional<Throwable>> autism$onFinish;
    private final boolean autism$fadeIn;
    private float autism$currentProgress;
    private long autism$fadeOutStart = -1L;
    private long autism$fadeInStart = -1L;

    public AutismLoadingOverlay(Minecraft minecraft, ReloadInstance reload,
                                   Consumer<Optional<Throwable>> onFinish, boolean fadeIn) {
        super(minecraft, reload, onFinish, fadeIn);
        this.autism$minecraft = minecraft;
        this.autism$reload = reload;
        this.autism$onFinish = onFinish;
        this.autism$fadeIn = fadeIn;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long now = Util.getMillis();

        if (this.autism$fadeIn && this.autism$fadeInStart == -1L) {
            this.autism$fadeInStart = now;
        }

        float fadeOutAnim = this.autism$fadeOutStart > -1L ? (float) (now - this.autism$fadeOutStart) / (float) FADE_OUT_TIME : -1.0F;
        float fadeInAnim = this.autism$fadeInStart > -1L ? (float) (now - this.autism$fadeInStart) / (float) FADE_IN_TIME : -1.0F;
        float logoAlpha;

        if (fadeOutAnim >= 1.0F) {
            if (this.autism$minecraft.gui.screen() != null) {
                this.autism$tryRenderScreen(graphics, 0, 0, a);
            } else {
                this.autism$minecraft.gui.hud.extractDeferredSubtitles();
            }

            int alpha = Mth.ceil((1.0F - Mth.clamp(fadeOutAnim - 1.0F, 0.0F, 1.0F)) * 255.0F);
            graphics.nextStratum();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, width, height), replaceAlpha(BG_COLOR, alpha));
            logoAlpha = 1.0F - Mth.clamp(fadeOutAnim - 1.0F, 0.0F, 1.0F);
        } else if (this.autism$fadeIn) {
            if (this.autism$minecraft.gui.screen() != null && fadeInAnim < 1.0F) {
                this.autism$tryRenderScreen(graphics, mouseX, mouseY, a);
            } else {
                this.autism$minecraft.gui.hud.extractDeferredSubtitles();
            }

            int alpha = Mth.ceil(Mth.clamp((double) fadeInAnim, 0.15, 1.0) * 255.0);
            graphics.nextStratum();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, width, height), replaceAlpha(BG_COLOR, alpha));
            logoAlpha = Mth.clamp(fadeInAnim, 0.0F, 1.0F);
        } else {
            this.autism$minecraft.gameRenderer.gameRenderState().guiRenderState.clearColorOverride = colorVector(BG_COLOR);
            logoAlpha = 1.0F;
        }

        if (logoAlpha > 0.0F) {
            drawCustomLogo(graphics, width, height, logoAlpha);
        }

        float actualProgress = this.autism$reload.getActualProgress();
        this.autism$currentProgress = Mth.clamp(this.autism$currentProgress * 0.95F + actualProgress * 0.050000012F, 0.0F, 1.0F);

        if (fadeOutAnim < 1.0F) {
            drawProgressBar(graphics, width, height, fadeOutAnim);
        }

        if (fadeOutAnim >= 2.0F) {
            this.autism$minecraft.gui.setOverlay(null);
        }
    }

    private void autism$tryRenderScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        net.minecraft.client.gui.screens.Screen screen = this.autism$minecraft.gui.screen();
        if (screen == null) return;
        try {
            screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, a);
        } catch (Exception ignored) {  }
    }

    private void drawCustomLogo(GuiGraphicsExtractor graphics, int width, int height, float alpha) {
        int centerX = width / 2;
        int centerY = height / 2;

        double maxW = width * 0.85;
        double maxH = height * 0.85;
        double scale = Math.min(maxW / LOGO_WIDTH, maxH / LOGO_HEIGHT) * 0.95;

        int drawW = (int) (LOGO_WIDTH * scale);
        int drawH = (int) (LOGO_HEIGHT * scale);

        int x = centerX - drawW / 2;
        int y = centerY - drawH / 2;
        int color = ARGB.white(alpha);

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            AutismThemeTextures.recolored(CUSTOM_LOGO, Channel.ACCENT),
            x, y, 0.0F, 0.0F,
            drawW, drawH,
            LOGO_WIDTH, LOGO_HEIGHT,
            LOGO_WIDTH, LOGO_HEIGHT,
            color
        );
    }

    private void drawProgressBar(GuiGraphicsExtractor graphics, int width, int height, float fadeOutAnim) {
        float barFade = 1.0F - Mth.clamp(fadeOutAnim, 0.0F, 1.0F);

        double maxW = width * 0.85;
        double maxH = height * 0.85;
        double scale = Math.min(maxW / LOGO_WIDTH, maxH / LOGO_HEIGHT) * 0.95;
        int drawW = (int) (LOGO_WIDTH * scale);
        int drawH = (int) (LOGO_HEIGHT * scale);

        int centerX = width / 2;
        int logoBottom = height / 2 + drawH / 2;
        int barY = logoBottom + 10;

        int x0 = centerX - drawW / 2;
        int y0 = barY - 5;
        int x1 = centerX + drawW / 2;
        int y1 = barY + 5;

        int barWidth = Mth.ceil((x1 - x0 - 2) * this.autism$currentProgress);
        int alpha = Math.round(barFade * 255.0F);
        int barColor = AutismTheme.recolor(ARGB.color(alpha, BAR_R, BAR_G, BAR_B), Channel.ACCENT);

        if (barWidth > 0) {
            UiRenderer.rect(graphics, UiBounds.of(x0 + 2, y0 + 2, barWidth, y1 - y0 - 4), barColor);
        }

        UiRenderer.outline(graphics, UiBounds.of(x0, y0, x1 - x0, y1 - y0), barColor);
    }

    private static int replaceAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | (alpha << 24);
    }

    private static Vector4f colorVector(int color) {
        return new Vector4f(
            (float) ((color >>> 16) & 0xFF) / 255.0F,
            (float) ((color >>> 8) & 0xFF) / 255.0F,
            (float) (color & 0xFF) / 255.0F,
            (float) ((color >>> 24) & 0xFF) / 255.0F
        );
    }

    @Override
    public void tick() {
        if (this.autism$fadeOutStart == -1L && this.autism$reload.isDone() && this.autism$isReadyToFadeOut()) {
            try {
                this.autism$reload.checkExceptions();
                this.autism$onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                this.autism$onFinish.accept(Optional.of(t));
            }

            this.autism$fadeOutStart = Util.getMillis();
            if (this.autism$minecraft.gui.screen() != null) {
                Window window = this.autism$minecraft.getWindow();
                this.autism$minecraft.gui.screen().init(window.getGuiScaledWidth(), window.getGuiScaledHeight());
            }
        }
    }

    private boolean autism$isReadyToFadeOut() {
        return !this.autism$fadeIn || this.autism$fadeInStart > -1L && Util.getMillis() - this.autism$fadeInStart >= 1000L;
    }

    @Override
    public boolean isPausing() {
        return true;
    }
}
