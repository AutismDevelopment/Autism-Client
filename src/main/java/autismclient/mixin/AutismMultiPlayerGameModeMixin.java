package autismclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismFakeGamemode;
import autismclient.modules.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class AutismMultiPlayerGameModeMixin {

    @WrapOperation(
        method = "interact",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void autism$routeMultiPovEntityInteraction(ClientPacketListener listener, Packet<?> packet,
                                                        Operation<Void> original) {
        if (packet instanceof ServerboundInteractPacket interact
            && autismclient.util.multi.MultiPilot.rerouteVanillaInteraction(interact)) {
            return;
        }
        original.call(listener, packet);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void autism$skipTickWithoutPlayer(CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) ci.cancel();
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void autism$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (ModuleRegistry.onStartDestroyBlock(pos, direction)) cir.setReturnValue(true);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void autism$routeBlockCapture(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (hit == null || !AutismSharedState.get().hasBlockCaptureCallback()) return;
        if (AutismSharedState.get().consumeBlockCaptureCallback(hit.getBlockPos(), hit.getDirection())) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    @Inject(
        method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getId()I", ordinal = 0)
    )
    private void autism$onBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        ModuleRegistry.onBlockBreakingProgress(pos, direction);
    }

    @ModifyExpressionValue(
        method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F")
    )
    private float autism$modifyFastBreakDestroyProgress(float original, BlockPos pos, Direction direction) {
        return ModuleRegistry.modifyBlockDestroyProgress(original, pos);
    }

    @Inject(method = "setLocalMode(Lnet/minecraft/world/level/GameType;)V", at = @At("RETURN"))
    private void autism$trackServerMode(GameType mode, CallbackInfo ci) {
        AutismFakeGamemode.onVanillaLocalMode(mode);
    }

    @Inject(method = "setLocalMode(Lnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;)V", at = @At("RETURN"))
    private void autism$trackServerMode(GameType mode, @Nullable GameType previousMode, CallbackInfo ci) {
        AutismFakeGamemode.onVanillaLocalMode(mode);
    }
}
