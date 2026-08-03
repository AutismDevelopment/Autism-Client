package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class AutismNoRenderTotemMixin {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noTotemAnimation(ItemStack stack, CallbackInfo ci) {
        if (NoRenderState.noTotemAnimation()) ci.cancel();
    }
}
