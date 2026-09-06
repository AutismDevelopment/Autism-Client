package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public abstract class AutismNoRenderWorldBorderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noWorldBorder(CallbackInfo ci) {
        if (NoRenderState.noWorldBorder()) ci.cancel();
    }
}
