package autismclient.mixin;

import autismclient.util.AutismHudManager;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerEventHandler.class)
public interface AutismContainerEventHandlerMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void autism$musicDisplayClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        if (!((Object) this instanceof Screen screen)) return;
        if (event.button() != 0) return;
        if (AutismHudManager.musicDisplayMouseClicked(
                AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void autism$musicDisplayDrag(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (autismclient.util.AutismLiteVariant.enabled()) return;
        if (!((Object) this instanceof Screen screen)) return;
        if (AutismHudManager.musicDisplayMouseDragged(
                AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void autism$musicDisplayRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (autismclient.util.AutismLiteVariant.enabled()) return;
        if (!((Object) this instanceof Screen screen)) return;
        if (AutismHudManager.musicDisplayMouseReleased(screen)) {
            cir.setReturnValue(true);
        }
    }
}
