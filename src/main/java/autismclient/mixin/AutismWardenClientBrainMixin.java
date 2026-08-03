package autismclient.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Warden.class)
public abstract class AutismWardenClientBrainMixin extends Monster {
    protected AutismWardenClientBrainMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void autism$skipClientBrainPush(Entity entity, CallbackInfo ci) {
        if (!this.level().isClientSide()) return;

        super.doPush(entity);
        ci.cancel();
    }
}
