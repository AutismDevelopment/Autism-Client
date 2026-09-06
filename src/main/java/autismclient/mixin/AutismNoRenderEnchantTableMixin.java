package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantTableRenderer.class)
public abstract class AutismNoRenderEnchantTableMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noEnchantTableBook(CallbackInfo ci) {
        if (NoRenderState.noEnchantTableBook()) ci.cancel();
    }
}
