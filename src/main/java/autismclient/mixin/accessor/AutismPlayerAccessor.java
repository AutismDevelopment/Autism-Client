package autismclient.mixin.accessor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface AutismPlayerAccessor {
    @Invoker("getEnchantedDamage")
    float autism$getEnchantedDamage(Entity entity, float dmg, DamageSource damageSource);

    @Invoker("getBlockSpeedFactor")
    float autism$getBlockSpeedFactor();
}
