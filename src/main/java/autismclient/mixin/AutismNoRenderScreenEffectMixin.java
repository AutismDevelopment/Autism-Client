package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class AutismNoRenderScreenEffectMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void autism$noFireOverlay(CallbackInfo ci) {
        if (NoRenderState.noFireOverlay()) ci.cancel();
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true, require = 0)
    private static void autism$noLiquidOverlay(CallbackInfo ci) {
        if (NoRenderState.noLiquidOverlay()) ci.cancel();
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true, require = 0)
    private static void autism$noInWallOverlay(CallbackInfo ci) {
        if (NoRenderState.noInWallOverlay()) ci.cancel();
    }
}
