package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.EndSpikeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndSpikeFeature.class)
public abstract class AutismOreSimEndSpikeFeatureMixin {
    @Inject(
        method = "placeSpike",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;getLevel()Lnet/minecraft/server/level/ServerLevel;"
        ),
        cancellable = true
    )
    private void autism$skipSyntheticEndCrystal(ServerLevelAccessor level, RandomSource random,
                                                 EndSpikeConfiguration config, EndSpikeFeature.EndSpike spike,
                                                 CallbackInfo ci) {
        if (level instanceof AutismSyntheticLevel) ci.cancel();
    }
}
