package autismclient.mixin;

import autismclient.modules.PackHideState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class AutismPanicMenuMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void autism$hideForeignWidgetsWhileHidden(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!PackHideState.isActive()) return;
        Screen self = (Screen) (Object) this;
        String screenClass = self.getClass().getName();
        boolean vanillaScreen = screenClass.startsWith("net.minecraft.") || screenClass.startsWith("com.mojang.");
        if (!vanillaScreen) return;
        for (GuiEventListener child : self.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            String className = widget.getClass().getName();
            if (className.startsWith("net.minecraft.") || className.startsWith("com.mojang.")) continue;
            widget.visible = false;
            widget.active = false;
        }
    }
}
