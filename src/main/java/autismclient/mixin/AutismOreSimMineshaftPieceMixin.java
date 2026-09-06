package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftCorridor")
public abstract class AutismOreSimMineshaftPieceMixin {
    @Inject(
        method = "createChest",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getLevel()Lnet/minecraft/server/level/ServerLevel;"),
        cancellable = true
    )
    private void autism$skipSyntheticChestEntity(WorldGenLevel level, BoundingBox chunkBox,
                                                  RandomSource random, int x, int y, int z,
                                                  ResourceKey<LootTable> lootTable,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof AutismSyntheticLevel) {

            random.nextLong();
            cir.setReturnValue(true);
        }
    }
}
