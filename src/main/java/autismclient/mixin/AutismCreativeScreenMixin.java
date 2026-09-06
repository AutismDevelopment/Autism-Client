package autismclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import autismclient.modules.AutismModule;
import autismclient.util.AutismOverlayManager;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.CreativeModeTab;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class AutismCreativeScreenMixin {
    @Shadow protected abstract void extractTabButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CreativeModeTab tab);

    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<Boolean> autism$inSafeRecall = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$blockCoveredTabHover(GuiGraphicsExtractor graphics, CreativeModeTab tab, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive()) return;
        if (AutismOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractTabButton", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$blockCoveredTabCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CreativeModeTab tab, CallbackInfo ci) {
        if (autism$inSafeRecall.get()) return;
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive()) return;
        if (!AutismOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) return;

        autism$inSafeRecall.set(Boolean.TRUE);
        try {
            this.extractTabButton(graphics, AutismOverlayManager.HOVER_BLOCKED_MOUSE, AutismOverlayManager.HOVER_BLOCKED_MOUSE, tab);
        } finally {
            autism$inSafeRecall.set(Boolean.FALSE);
        }
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive()) return;

        if (AutismOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive()) return;

        if (AutismOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$mouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive()) return;

        if (AutismOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive()) return;

        if (AutismOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive()) return;

        if (AutismOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$charTyped(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive()) return;

        if (AutismOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            cir.setReturnValue(true);
        }
    }
}
