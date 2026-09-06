package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.stream.IntStream;

@Mixin(CappedProcessor.class)
public abstract class AutismOreSimCappedProcessorMixin {
    @Shadow @Final private StructureProcessor delegate;
    @Shadow @Final private IntProvider limit;

    @WrapMethod(method = "finalizeProcessing")
    private List<StructureTemplate.StructureBlockInfo> autism$useSyntheticSeed(
        ServerLevelAccessor level,
        BlockPos position,
        BlockPos referencePos,
        List<StructureTemplate.StructureBlockInfo> originalBlocks,
        List<StructureTemplate.StructureBlockInfo> processedBlocks,
        StructurePlaceSettings settings,
        Operation<List<StructureTemplate.StructureBlockInfo>> original
    ) {
        if (!(level instanceof AutismSyntheticLevel synthetic)) {
            return original.call(level, position, referencePos, originalBlocks, processedBlocks, settings);
        }
        if (limit.maxInclusive() == 0 || processedBlocks.isEmpty()) return processedBlocks;
        if (originalBlocks.size() != processedBlocks.size()) {
            Util.logAndPauseIfInIde("Original block info list not in sync with processed list, skipping processing. "
                + "Original size: " + originalBlocks.size() + ", Processed size: " + processedBlocks.size());
            return processedBlocks;
        }

        RandomSource random = RandomSource.createThreadLocalInstance(synthetic.getSeed())
            .forkPositional().at(position);
        int maxToReplace = Math.min(limit.sample(random), processedBlocks.size());
        if (maxToReplace < 1) return processedBlocks;
        IntArrayList indices = Util.toShuffledList(IntStream.range(0, processedBlocks.size()), random);
        IntIterator iterator = indices.intIterator();
        int replaced = 0;
        while (iterator.hasNext() && replaced < maxToReplace) {
            int index = iterator.nextInt();
            StructureTemplate.StructureBlockInfo originalInfo = originalBlocks.get(index);
            StructureTemplate.StructureBlockInfo processedInfo = processedBlocks.get(index);
            StructureTemplate.StructureBlockInfo altered = delegate.processBlock(
                level, position, referencePos, originalInfo.pos(), processedInfo, settings);
            if (altered != null && !processedInfo.equals(altered)) {
                replaced++;
                processedBlocks.set(index, altered);
            }
        }
        return processedBlocks;
    }
}
