package autismclient.mixin;

import autismclient.modules.ModuleRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class AutismBlockBehaviourMixin {
    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void autism$xrayAmbientOcclusion(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (ModuleRenderUtil.hasXrayRenderWork()) cir.setReturnValue(1.0F);
    }
}
