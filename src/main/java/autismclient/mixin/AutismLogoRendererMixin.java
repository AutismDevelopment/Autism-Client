package autismclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LogoRenderer.class)
public class AutismLogoRendererMixin {
    @Unique
    private static final Identifier PACKUTIL_LOGO = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/autism_client_logo.png");
    @Unique
    private static final int PACKUTIL_LOGO_TEXTURE_WIDTH = 516;
    @Unique
    private static final int PACKUTIL_LOGO_TEXTURE_HEIGHT = 144;
    @Unique
    private static final int PACKUTIL_LOGO_MAX_WIDTH = 320;
    @Unique
    private static final int PACKUTIL_LOGO_MAX_HEIGHT = 72;
    @Unique
    private static final int PACKUTIL_LOGO_Y_OFFSET = 0;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
    private void autism$renderHighResolutionLogo(GuiGraphicsExtractor graphics, int width, float alpha, int heightOffset, CallbackInfo ci) {

        if (autismclient.util.AutismMenuPrefs.vanillaMenuVisuals()) return;
        int maxWidth = Math.min(PACKUTIL_LOGO_MAX_WIDTH, Math.max(180, width - 40));
        float scale = Math.min(
            maxWidth / (float) PACKUTIL_LOGO_TEXTURE_WIDTH,
            PACKUTIL_LOGO_MAX_HEIGHT / (float) PACKUTIL_LOGO_TEXTURE_HEIGHT
        );
        int drawWidth = Math.round(PACKUTIL_LOGO_TEXTURE_WIDTH * scale);
        int drawHeight = Math.round(PACKUTIL_LOGO_TEXTURE_HEIGHT * scale);
        int logoX = width / 2 - drawWidth / 2;
        int logoY = heightOffset + PACKUTIL_LOGO_Y_OFFSET;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            autismclient.util.AutismThemeTextures.recolored(PACKUTIL_LOGO, autismclient.util.AutismTheme.Channel.ACCENT),
            logoX,
            logoY,
            0.0F,
            0.0F,
            drawWidth,
            drawHeight,
            PACKUTIL_LOGO_TEXTURE_WIDTH,
            PACKUTIL_LOGO_TEXTURE_HEIGHT,
            PACKUTIL_LOGO_TEXTURE_WIDTH,
            PACKUTIL_LOGO_TEXTURE_HEIGHT,
            ARGB.white(alpha)
        );
        ci.cancel();
    }
}
