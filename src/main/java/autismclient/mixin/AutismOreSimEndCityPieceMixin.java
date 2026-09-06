package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCityPieces.EndCityPiece.class)
public abstract class AutismOreSimEndCityPieceMixin {
    @Inject(method = "handleDataMarker", at = @At("HEAD"), cancellable = true)
    private void autism$skipSyntheticEntityMarker(String marker, BlockPos position, ServerLevelAccessor level,
                                                   RandomSource random, BoundingBox chunkBox, CallbackInfo ci) {
        if (level instanceof AutismSyntheticLevel
            && (marker.startsWith("Sentry") || marker.startsWith("Elytra"))) {
            ci.cancel();
        }
    }
}
