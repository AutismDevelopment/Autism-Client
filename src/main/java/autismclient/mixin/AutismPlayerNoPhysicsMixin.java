package autismclient.mixin;

import autismclient.modules.NoClipModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class AutismPlayerNoPhysicsMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z",
        ordinal = 1, shift = At.Shift.BEFORE))
    private void autism$noClipNoPhysics(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self != Minecraft.getInstance().player) return;
        if (!self.noPhysics && NoClipModule.holdsNoPhysics()) self.noPhysics = true;
    }
}
