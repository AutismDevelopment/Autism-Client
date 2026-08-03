package autismclient.mixin;

import autismclient.modules.EntityControlModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractBoat.class)
public abstract class AutismAbstractBoatEntityControlMixin {
    @ModifyExpressionValue(method = "controlBoat", at = @At(
        value = "FIELD",
        target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;inputLeft:Z",
        opcode = Opcodes.GETFIELD))
    private boolean autism$lockLeftTurn(boolean original) {
        return EntityControlModule.shouldLockBoatYaw() ? false : original;
    }

    @ModifyExpressionValue(method = "controlBoat", at = @At(
        value = "FIELD",
        target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;inputRight:Z",
        opcode = Opcodes.GETFIELD))
    private boolean autism$lockRightTurn(boolean original) {
        return EntityControlModule.shouldLockBoatYaw() ? false : original;
    }
}
