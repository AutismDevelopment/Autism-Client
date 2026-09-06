package autismclient.mixin;

import autismclient.modules.ModuleMovementUtil;
import autismclient.modules.PackFreecamState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class AutismEntityMovementMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 autism$modifyLocalPlayerMovement(Vec3 movement, MoverType type) {
        return ModuleMovementUtil.onPlayerMove((Entity) (Object) this, type, movement);
    }

    @Redirect(
        method = "getBlockBounciness",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getBounceRestitution()F")
    )
    private float autism$antiBounceRestitution(Block block) {
        Entity entity = (Entity) (Object) this;
        if (block instanceof SlimeBlock && ModuleMovementUtil.shouldCancelNoFallBounce(entity)) {
            return 0.0F;
        }
        return block.getBounceRestitution();
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void autism$freecamTurn(double deltaYaw, double deltaPitch, CallbackInfo ci) {
        if ((Object) this != net.minecraft.client.Minecraft.getInstance().player) return;

        if (PackFreecamState.isActive()) {
            PackFreecamState.turn(deltaYaw, deltaPitch);
            ci.cancel();
            return;
        }

        if (autismclient.util.multi.MultiPilot.isActive()) {
            autismclient.util.multi.MultiPilot.handleTurn(deltaYaw, deltaPitch);
            ci.cancel();
            return;
        }

        if (autismclient.util.AutismRemoteView.isActive()) {
            ci.cancel();
        }
    }
}
