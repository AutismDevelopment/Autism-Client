package autismclient.mixin;

import autismclient.modules.NameCensorModule;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BossEvent.class)
public class AutismBossEventNameCensorMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void autism$censorBossName(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NameCensorModule.censorServerComponent(cir.getReturnValue()));
    }
}
