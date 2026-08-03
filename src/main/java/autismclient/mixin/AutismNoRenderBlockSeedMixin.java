package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class AutismNoRenderBlockSeedMixin {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$constantSeed(BlockState state, BlockPos pos, CallbackInfoReturnable<Long> cir) {
        if (NoRenderState.noTextureRotations()) cir.setReturnValue(0L);
    }
}
