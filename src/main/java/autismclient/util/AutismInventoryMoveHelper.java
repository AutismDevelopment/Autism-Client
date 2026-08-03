package autismclient.util;

import autismclient.modules.AutismModule;
import autismclient.util.macro.MacroExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.KeyMapping;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class AutismInventoryMoveHelper {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Set<KeyMapping> OWNED_KEYS = Collections.newSetFromMap(new IdentityHashMap<>());

    private AutismInventoryMoveHelper() {
    }

    public static boolean handleKeyEvent(KeyEvent input, boolean pressed) {
        if (input == null) return false;

        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive() || !module.isInventoryMoveEnabled()) {
            releaseMovementKeysIfSafe();
            return false;
        }
        if (MacroExecutor.isControllingInput()) {
            releaseMovementKeysIfSafe();
            return false;
        }
        if (!shouldHandleCurrentScreen()) {
            releaseMovementKeysIfSafe();
            return false;
        }
        if (AutismOverlayManager.get().isAnyTextFieldFocused()) {
            releaseMovementKeysIfSafe();
            return false;
        }
        if (MC == null || MC.options == null) {
            releaseMovementKeysIfSafe();
            return false;
        }

        boolean handled = false;
        handled |= pass(MC.options.keyUp, input, pressed);
        handled |= pass(MC.options.keyDown, input, pressed);
        handled |= pass(MC.options.keyLeft, input, pressed);
        handled |= pass(MC.options.keyRight, input, pressed);
        handled |= pass(MC.options.keyJump, input, pressed);
        handled |= pass(MC.options.keyShift, input, pressed);
        handled |= pass(MC.options.keySprint, input, pressed);
        return handled;
    }

    public static void syncHeldMovementKeysIfSafe() {
        if (!canHandleMovementKeys()) return;

        sync(MC.options.keyUp);
        sync(MC.options.keyDown);
        sync(MC.options.keyLeft);
        sync(MC.options.keyRight);
        sync(MC.options.keyJump);
        sync(MC.options.keyShift);
        sync(MC.options.keySprint);
    }

    public static void releaseMovementKeysIfSafe() {
        if (MC == null || MC.options == null) return;

        releaseOwned(MC.options.keyUp);
        releaseOwned(MC.options.keyDown);
        releaseOwned(MC.options.keyLeft);
        releaseOwned(MC.options.keyRight);
        releaseOwned(MC.options.keyJump);
        releaseOwned(MC.options.keyShift);
        releaseOwned(MC.options.keySprint);
    }

    public static void resyncMovementKeysAfterPov() {
        if (MC == null || MC.options == null) return;
        if (AutismOverlayManager.get().isAnyTextFieldFocused()
            || MC.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen
            || MC.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
            || MC.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen
            || MC.gui.screen() != null && !shouldHandleCurrentScreen()) {
            releaseMovementKeysIfSafe();
            return;
        }
        sync(MC.options.keyUp);
        sync(MC.options.keyDown);
        sync(MC.options.keyLeft);
        sync(MC.options.keyRight);
        sync(MC.options.keyJump);
        sync(MC.options.keyShift);
        sync(MC.options.keySprint);
    }

    private static boolean pass(KeyMapping binding, KeyEvent input, boolean pressed) {
        if (binding == null || !binding.matches(input)) return false;
        binding.setDown(pressed);
        if (pressed) OWNED_KEYS.add(binding);
        else OWNED_KEYS.remove(binding);
        return true;
    }

    private static void sync(KeyMapping binding) {
        if (binding == null) return;
        boolean pressed = AutismKeyMappingBridge.of(binding).autism$isActuallyDown();
        binding.setDown(pressed);
        if (pressed) OWNED_KEYS.add(binding);
        else OWNED_KEYS.remove(binding);
    }

    private static void releaseOwned(KeyMapping binding) {
        if (binding == null) return;
        if (OWNED_KEYS.remove(binding)) {
            binding.setDown(false);
        }
    }

    private static boolean shouldHandleCurrentScreen() {
        if (MC == null || MC.gui.screen() == null) return false;
        net.minecraft.client.gui.screens.Screen screen = MC.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return false;
        if (screen instanceof AnvilScreen) return false;
        if (screen instanceof AbstractCommandBlockEditScreen) return false;
        if (screen instanceof StructureBlockEditScreen) return false;

        if (screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox) return false;
        return true;
    }

    private static boolean canHandleMovementKeys() {
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive() || !module.isInventoryMoveEnabled()) {
            releaseMovementKeysIfSafe();
            return false;
        }
        if (MacroExecutor.isControllingInput()) {
            releaseMovementKeysIfSafe();
            return false;
        }
        if (!shouldHandleCurrentScreen()) {
            releaseMovementKeysIfSafe();
            return false;
        }
        if (AutismOverlayManager.get().isAnyTextFieldFocused()) {
            releaseMovementKeysIfSafe();
            return false;
        }
        if (MC == null || MC.options == null) {
            releaseMovementKeysIfSafe();
            return false;
        }
        return true;
    }
}
