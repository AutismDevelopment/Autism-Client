package autismclient.mixin;

import autismclient.modules.NameCensorModule;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.client.renderer.entity.DisplayRenderer$TextDisplayRenderer")
public class AutismTextDisplayRendererNameCensorMixin {
    @ModifyVariable(method = "splitLines", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component autism$censorTextDisplay(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }
}
