package autismclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class AutismLocalPlayerZeroIdMixin {
    @Unique
    private boolean autism$localPlayerZeroIdAssigned;

    @Inject(method = "setId", at = @At("HEAD"))
    private void autism$rememberExplicitLocalPlayerId(int id, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        autism$localPlayerZeroIdAssigned = id == 0
            && minecraft != null
            && minecraft.player == (Object) this;
    }

    @Inject(method = "getId", at = @At("HEAD"), cancellable = true)
    private void autism$allowExplicitLocalPlayerZeroId(CallbackInfoReturnable<Integer> cir) {
        if (!autism$localPlayerZeroIdAssigned) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player == (Object) this) cir.setReturnValue(0);
    }
}
