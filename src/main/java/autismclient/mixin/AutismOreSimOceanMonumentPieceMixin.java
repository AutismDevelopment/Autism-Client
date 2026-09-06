package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces$OceanMonumentPiece")
public abstract class AutismOreSimOceanMonumentPieceMixin {
    @Inject(method = "spawnElder", at = @At("HEAD"), cancellable = true)
    private void autism$skipSyntheticEntity(WorldGenLevel level, BoundingBox chunkBox,
                                             int x, int y, int z, CallbackInfo ci) {
        if (level instanceof AutismSyntheticLevel) ci.cancel();
    }
}
