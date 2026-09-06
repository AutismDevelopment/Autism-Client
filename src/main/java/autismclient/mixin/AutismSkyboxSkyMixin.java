package autismclient.mixin;

import autismclient.modules.SkyboxRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class AutismSkyboxSkyMixin {
    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$skyboxPanorama(CallbackInfo ci) {
        if (!SkyboxRenderer.isActive()) return;
        SkyboxRenderer.render(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        ci.cancel();
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$skyboxNoSunMoonStars(CallbackInfo ci) {
        if (SkyboxRenderer.isActive()) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$skyboxNoSunriseGlow(CallbackInfo ci) {
        if (SkyboxRenderer.isActive()) ci.cancel();
    }
}
