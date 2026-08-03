package autismclient.mixin;

import autismclient.modules.EntityControlModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Strider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Pig.class, Strider.class})
public abstract class AutismSteerableEntityControlMixin {
    @ModifyReturnValue(method = "getControllingPassenger", at = @At("RETURN"))
    private LivingEntity autism$entityControlSteer(LivingEntity original) {
        if (original == null && EntityControlModule.shouldControlSteer((Entity) (Object) this)) {
            return Minecraft.getInstance().player;
        }
        return original;
    }
}
