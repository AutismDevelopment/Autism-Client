package autismclient.mixin;

import autismclient.util.worldgen.mc26_2.AutismSyntheticFeatureBridge;
import autismclient.util.worldgen.mc26_2.AutismSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.treedecorators.PaleMossDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PaleMossDecorator.class)
public abstract class AutismOreSimPaleMossDecoratorMixin {
    @Shadow @Final private float leavesProbability;
    @Shadow @Final private float trunkProbability;
    @Shadow @Final private float groundProbability;

    @WrapMethod(method = "place")
    private void autism$placeInSyntheticWorld(TreeDecorator.Context context, Operation<Void> original) {
        if (context.level() instanceof AutismSyntheticLevel synthetic) {
            AutismSyntheticFeatureBridge.placePaleMoss(
                context, synthetic, leavesProbability, trunkProbability, groundProbability);
            return;
        }
        original.call(context);
    }
}
