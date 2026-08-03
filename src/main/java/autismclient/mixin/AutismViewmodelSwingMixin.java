package autismclient.mixin;

import autismclient.modules.ViewmodelState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class AutismViewmodelSwingMixin {
    @ModifyExpressionValue(method = "getCurrentSwingDuration",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/SwingAnimation;duration()I"),
        require = 0)
    private int autism$swingDuration(int duration) {
        if (ViewmodelState.active()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player == (Object) this) return ViewmodelState.swingDuration();
        }
        return duration;
    }
}
