package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticFeatureBridge;
import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TemplateFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TemplateFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TemplateFeature.class)
public abstract class AutismOreSimTemplateFeatureMixin {
    @WrapMethod(method = "place")
    private boolean autism$placeInSyntheticWorld(FeaturePlaceContext<TemplateFeatureConfiguration> context,
                                                  Operation<Boolean> original) {
        if (context.level() instanceof AutismSyntheticLevel synthetic) {
            return AutismSyntheticFeatureBridge.placeTemplate(context, synthetic);
        }
        return original.call(context);
    }
}
