package autismclient.mixin;

import autismclient.modules.CrystalAuraModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EndCrystalModel.class)
public abstract class AutismCrystalViewModelMixin {
    @ModifyExpressionValue(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EndCrystalRenderer;getY(F)F"),
        require = 0)
    private float autism$crystalBounce(float original) {
        return CrystalAuraModule.crystalViewActive()
            ? original * CrystalAuraModule.crystalViewBounce() : original;
    }

    @ModifyVariable(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V",
        at = @At(value = "STORE", ordinal = 0),
        require = 0)
    private float autism$crystalSpin(float animationAngle) {
        return CrystalAuraModule.crystalViewActive()
            ? animationAngle * CrystalAuraModule.crystalViewSpin() : animationAngle;
    }
}
