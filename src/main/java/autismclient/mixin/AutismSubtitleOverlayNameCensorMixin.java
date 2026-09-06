package autismclient.mixin;

import autismclient.modules.NameCensorModule;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubtitleOverlay.class)
public class AutismSubtitleOverlayNameCensorMixin {
    @ModifyArg(
        method = "onPlaySound",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/SubtitleOverlay$Subtitle;<init>(Lnet/minecraft/network/chat/Component;FLnet/minecraft/world/phys/Vec3;)V"),
        index = 0,
        require = 0
    )
    private Component autism$censorSubtitle(Component component) {
        return NameCensorModule.censorComponent(component);
    }
}
