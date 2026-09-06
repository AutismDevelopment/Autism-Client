package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticFeatureBridge;
import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.FossilFeature;
import net.minecraft.world.level.levelgen.feature.FossilFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FossilFeature.class)
public abstract class AutismOreSimFossilFeatureMixin {
    @WrapMethod(method = "place")
    private boolean autism$placeInSyntheticWorld(FeaturePlaceContext<FossilFeatureConfiguration> context,
                                                  Operation<Boolean> original) {
        if (context.level() instanceof AutismSyntheticLevel synthetic) {
            return AutismSyntheticFeatureBridge.placeFossil(context, synthetic);
        }
        return original.call(context);
    }
}
