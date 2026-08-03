package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class AutismNoRenderCloudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noClouds(CallbackInfo ci) {
        if (NoRenderState.noClouds()) ci.cancel();
    }
}
