package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class AutismNoRenderClientLevelMixin {
    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noDestroyParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (NoRenderState.noBlockBreakParticles()) ci.cancel();
    }

    @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
        if (NoRenderState.noBlockBreakParticles()) ci.cancel();
    }
}
