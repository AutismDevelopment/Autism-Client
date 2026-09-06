package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconRenderer.class)
public abstract class AutismNoRenderBeaconMixin {
    @Inject(method = "submitBeaconBeam", at = @At("HEAD"), cancellable = true, require = 0)
    private static void autism$noBeaconBeams(CallbackInfo ci) {
        if (NoRenderState.noBeaconBeams()) ci.cancel();
    }
}
