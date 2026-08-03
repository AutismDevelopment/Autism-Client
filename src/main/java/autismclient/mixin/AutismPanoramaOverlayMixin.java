package autismclient.mixin;

import autismclient.modules.PackHideState;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Panorama.class)
public abstract class AutismPanoramaOverlayMixin {
    @Unique
    private static final Identifier AUTISM_PANORAMA_OVERLAY =
        Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/background/panorama_overlay.png");

    @ModifyArg(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIII)V"
        ),
        index = 1
    )
    private Identifier autism$swapOverlay(Identifier original) {
        if (autismclient.util.AutismMenuPrefs.vanillaMenuVisuals()) return original;

        return autismclient.util.AutismThemeTextures.panoramaOverlay(AUTISM_PANORAMA_OVERLAY);
    }
}
