package autismclient.mixin;

import autismclient.modules.ModuleMovementUtil;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class AutismPlayerFlyingSpeedMixin {
    @Inject(method = "getFlyingSpeed", at = @At("HEAD"), cancellable = true)
    private void autism$flightFlyingSpeed(CallbackInfoReturnable<Float> cir) {
        float speed = ModuleMovementUtil.flightFlyingSpeed((Player) (Object) this);
        if (speed != -1.0f) cir.setReturnValue(speed);
    }
}
