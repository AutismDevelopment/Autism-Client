package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SwampHutPiece.class)
public abstract class AutismOreSimSwampHutPieceMixin {
    @Shadow private boolean spawnedWitch;
    @Shadow private boolean spawnedCat;

    @WrapMethod(method = "postProcess")
    private void autism$skipSyntheticEntities(WorldGenLevel level, StructureManager structureManager,
                                               ChunkGenerator generator, RandomSource random,
                                               BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot,
                                               Operation<Void> original) {
        if (level instanceof AutismSyntheticLevel) {
            spawnedWitch = true;
            spawnedCat = true;
        }
        original.call(level, structureManager, generator, random, chunkBox, chunkPos, pivot);
    }
}
