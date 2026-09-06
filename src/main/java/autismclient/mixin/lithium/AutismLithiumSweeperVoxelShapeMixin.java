package autismclient.mixin.lithium;

import autismclient.modules.AirJumpModule;
import autismclient.modules.PhaseModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeperVoxelShape;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(ChunkAwareBlockCollisionSweeperVoxelShape.class)
@SuppressWarnings("rawtypes")
public abstract class AutismLithiumSweeperVoxelShapeMixin extends ChunkAwareBlockCollisionSweeper {
    protected AutismLithiumSweeperVoxelShapeMixin(Level level, Entity entity, AABB box, boolean hideLastCollision) {
        super(level, entity, box, hideLastCollision);
    }

    @ModifyExpressionValue(method = "computeNext()Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape autism$ghostBlockShape(VoxelShape original, @Local(name = "state") BlockState state) {
        return PhaseModule.blockShape(AirJumpModule.ghostBlockShape(original, this.pos), state, this.pos);
    }
}
