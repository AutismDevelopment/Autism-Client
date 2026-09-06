package autismclient.mixin;

import autismclient.modules.ModuleMovementUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public abstract class AutismSprintInputMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"))
    private Input autism$sprintInputRecord(Input original) {

        if (!original.forward()) return original;
        if (original.sprint() || !ModuleMovementUtil.sprintDecision(false, false)) return original;
        return new Input(original.forward(), original.backward(), original.left(), original.right(),
            original.jump(), original.shift(), true);
    }
}
