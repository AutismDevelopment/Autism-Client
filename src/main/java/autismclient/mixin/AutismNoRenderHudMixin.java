package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class AutismNoRenderHudMixin {
    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noPortalOverlay(GuiGraphicsExtractor ctx, float alpha, CallbackInfo ci) {
        if (NoRenderState.noPortalOverlay()) ci.cancel();
    }

    @Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noSpyglassOverlay(GuiGraphicsExtractor ctx, float scale, CallbackInfo ci) {
        if (NoRenderState.noSpyglassOverlay()) ci.cancel();
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noNausea(GuiGraphicsExtractor ctx, float amount, CallbackInfo ci) {
        if (NoRenderState.noNausea()) ci.cancel();
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noVignette(GuiGraphicsExtractor ctx, net.minecraft.world.entity.Entity entity, CallbackInfo ci) {
        if (NoRenderState.noVignette()) ci.cancel();
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noCrosshair(GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        if (NoRenderState.noCrosshair()) ci.cancel();
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noTitle(GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        if (NoRenderState.noTitle()) ci.cancel();
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noHeldItemName(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (NoRenderState.noHeldItemName()) ci.cancel();
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noPotionIcons(GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        if (NoRenderState.noPotionIcons()) ci.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noScoreboardSidebar(GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        if (NoRenderState.noScoreboard()) ci.cancel();
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noScoreboardDisplay(GuiGraphicsExtractor ctx, net.minecraft.world.scores.Objective objective, CallbackInfo ci) {
        if (NoRenderState.noScoreboard()) ci.cancel();
    }

    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noTextureOverlay(GuiGraphicsExtractor ctx, Identifier texture, float alpha, CallbackInfo ci) {
        if (texture == null) return;
        String path = texture.getPath();
        if (NoRenderState.noPumpkinOverlay() && path.contains("pumpkin")) ci.cancel();
        else if (NoRenderState.noPowderedSnowOverlay() && (path.contains("powder") || path.contains("snow"))) ci.cancel();
    }
}
