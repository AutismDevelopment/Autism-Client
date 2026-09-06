package autismclient.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Screen.class)
public abstract class AutismEssentialOverlayGuardMixin {
    @WrapMethod(method = "extractRenderState")
    private void autism$catchEssentialOverlayNpe(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
            Operation<Void> original) {
        try {
            original.call(graphics, mouseX, mouseY, delta);
        } catch (NullPointerException npe) {

            if (autism$isEssentialFault(npe)) return;
            throw npe;
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean autism$isEssentialFault(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            for (StackTraceElement el : cur.getStackTrace()) {
                if (el.getClassName().startsWith("gg.essential")) return true;
            }
        }
        return false;
    }
}
