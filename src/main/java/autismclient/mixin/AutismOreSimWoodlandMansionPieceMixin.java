package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WoodlandMansionPieces.WoodlandMansionPiece.class)
public abstract class AutismOreSimWoodlandMansionPieceMixin {
    @Inject(method = "handleDataMarker", at = @At("HEAD"), cancellable = true)
    private void autism$skipSyntheticMobMarker(String marker, BlockPos position, ServerLevelAccessor level,
                                                RandomSource random, BoundingBox chunkBox, CallbackInfo ci) {
        if (!(level instanceof AutismSyntheticLevel synthetic)) return;
        if (!"Mage".equals(marker) && !"Warrior".equals(marker) && !"Group of Allays".equals(marker)) return;
        if ("Group of Allays".equals(marker)) synthetic.getRandom().nextInt(3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        ci.cancel();
    }
}
