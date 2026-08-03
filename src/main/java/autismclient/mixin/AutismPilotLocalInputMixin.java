package autismclient.mixin;

import autismclient.util.multi.MultiPilot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class AutismPilotLocalInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void autism$freezeOwnInputWhilePiloting(CallbackInfo ci) {
        if (!MultiPilot.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.input != (Object) this) return;
        this.keyPresses = Input.EMPTY;
        this.moveVector = Vec2.ZERO;
        ci.cancel();
    }
}
