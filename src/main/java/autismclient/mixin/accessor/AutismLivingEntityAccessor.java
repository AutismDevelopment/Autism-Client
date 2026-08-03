package autismclient.mixin.accessor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface AutismLivingEntityAccessor {
    @Accessor("attackStrengthTicker")
    int autism$getAttackStrengthTicker();

    @Accessor("autoSpinAttackDmg")
    float autism$getAutoSpinAttackDmg();

    @Invoker("getDamageAfterArmorAbsorb")
    float autism$getDamageAfterArmorAbsorb(DamageSource source, float amount);

    @Invoker("getDamageAfterMagicAbsorb")
    float autism$getDamageAfterMagicAbsorb(DamageSource source, float amount);

    @Invoker("calculateFallDamage")
    int autism$calculateFallDamage(double fallDistance, float damageMultiplier);
}
