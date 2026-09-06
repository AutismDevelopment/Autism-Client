package autismclient.mixin;

import autismclient.util.AutismChamsContext;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class AutismChamsCapeLayerMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$hideCapeForChams(CallbackInfo ci) {
        if (AutismChamsContext.active()) ci.cancel();
    }
}