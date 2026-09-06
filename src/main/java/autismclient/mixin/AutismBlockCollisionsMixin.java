package autismclient.mixin;

import autismclient.modules.AirJumpModule;
import autismclient.modules.PhaseModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisions.class)
public abstract class AutismBlockCollisionsMixin {
    @Shadow
    @Final
    private BlockPos.MutableBlockPos pos;

    @ModifyExpressionValue(method = "computeNext", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))

    private VoxelShape autism$ghostBlockShape(VoxelShape original, @Local(ordinal = 0) BlockState state) {
        return PhaseModule.blockShape(AirJumpModule.ghostBlockShape(original, this.pos), state, this.pos);
    }
}
