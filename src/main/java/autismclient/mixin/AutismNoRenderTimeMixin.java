package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public abstract class AutismNoRenderTimeMixin {
    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$forceTime(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
        if (NoRenderState.timeChanged() && definition.is(WorldClocks.OVERWORLD)) {
            cir.setReturnValue(NoRenderState.timeTicks());
        }
    }
}
