package autismclient.mixin.accessor;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEffectInstance.class)
public interface AutismMobEffectInstanceAccessor {
    @Accessor("duration")
    void autism$setDuration(int duration);
}
