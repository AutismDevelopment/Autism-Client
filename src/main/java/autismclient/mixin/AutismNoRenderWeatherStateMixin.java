package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class AutismNoRenderWeatherStateMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (autism$override()) cir.setReturnValue(NoRenderState.rainLevel());
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (autism$override()) cir.setReturnValue(NoRenderState.thunderLevel());
    }

    @Unique
    private boolean autism$override() {
        return NoRenderState.weatherChanged() && ((Level) (Object) this).isClientSide();
    }
}
