package autismclient.mixin;

import autismclient.modules.ViewmodelState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientAvatarState.class)
public abstract class AutismViewmodelStrideMixin {
    @Shadow
    private float bob;

    @Inject(method = "updateBob", at = @At("RETURN"), require = 0)
    private void autism$airWalker(float movement, CallbackInfo ci) {
        if (!ViewmodelState.airWalker()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        this.bob = (float) Math.min(0.1, mc.player.getDeltaMovement().horizontalDistance());
    }
}
