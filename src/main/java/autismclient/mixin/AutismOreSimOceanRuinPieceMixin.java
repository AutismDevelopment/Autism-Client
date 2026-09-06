package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.OceanRuinPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OceanRuinPieces.OceanRuinPiece.class)
public abstract class AutismOreSimOceanRuinPieceMixin {
    @Inject(method = "handleDataMarker", at = @At("HEAD"), cancellable = true)
    private void autism$skipSyntheticDrowned(String marker, BlockPos position, ServerLevelAccessor level,
                                              RandomSource random, BoundingBox chunkBox, CallbackInfo ci) {
        if (!(level instanceof AutismSyntheticLevel) || !"drowned".equals(marker)) return;
        level.setBlock(position,
            position.getY() > level.getSeaLevel() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(),
            2);
        ci.cancel();
    }
}
