package autismclient.mixin.accessor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface AutismEntityAccessor {
    @Invoker("isInvulnerableToBase")
    boolean autism$isInvulnerableToBase(DamageSource source);

    @Invoker("getInputVector")
    static Vec3 autism$getInputVector(Vec3 relative, float motion, float facing) {
        throw new AssertionError();
    }
}
