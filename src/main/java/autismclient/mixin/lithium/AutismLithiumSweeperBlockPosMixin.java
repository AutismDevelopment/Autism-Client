package autismclient.mixin.lithium;

import autismclient.modules.AirJumpModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeperBlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(ChunkAwareBlockCollisionSweeperBlockPos.class)
@SuppressWarnings("rawtypes")
public abstract class AutismLithiumSweeperBlockPosMixin extends ChunkAwareBlockCollisionSweeper {
    protected AutismLithiumSweeperBlockPosMixin(Level level, Entity entity, AABB box, boolean hideLastCollision) {
        super(level, entity, box, hideLastCollision);
    }

    @ModifyExpressionValue(method = "computeNext()Lnet/minecraft/core/BlockPos$MutableBlockPos;", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape autism$ghostBlockShape(VoxelShape original) {
        return AirJumpModule.ghostBlockShape(original, this.pos);
    }
}
