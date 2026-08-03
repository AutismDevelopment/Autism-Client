package autismclient.mixin;

import autismclient.modules.ScaffoldModule;
import autismclient.modules.KillAuraModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public abstract class AutismScaffoldInputMixin extends ClientInput {
    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;")
    )
    private Input autism$optionalScaffoldStabilization(Input original) {
        Input scaffold = ScaffoldModule.modifyMovementInput(this, original);
        return KillAuraModule.modifyMovementInput(this, scaffold);
    }
}
