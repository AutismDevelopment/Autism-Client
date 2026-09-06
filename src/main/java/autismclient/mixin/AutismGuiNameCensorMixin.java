package autismclient.mixin;

import autismclient.modules.NameCensorModule;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Hud.class)
public class AutismGuiNameCensorMixin {
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component autism$censorTitle(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component autism$censorSubtitle(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component autism$censorOverlayMessage(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }
}
