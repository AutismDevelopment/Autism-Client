package autismclient.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import autismclient.modules.PackHideState;

public final class AutismInputClicker {
    private enum PacedUseOwner {
        NONE,
        FAST_BLOCK,
        FAST_EXP
    }

    private static final Minecraft MC = Minecraft.getInstance();
    private static boolean attackQueued;
    private static boolean useQueued;
    private static PacedUseOwner pacedUseQueued = PacedUseOwner.NONE;
    private static int hotbarSlotQueued = -1;
    private static boolean attackPressed;
    private static boolean usePressed;
    private static PacedUseOwner pacedUseActive = PacedUseOwner.NONE;
    private static PacedUseOwner pacedUseInProgress = PacedUseOwner.NONE;
    private static KeyMapping hotbarPressed;

    private AutismInputClicker() {
    }

    public static void queueAttackClick() {
        if (PackHideState.isHardLocked()) return;
        attackQueued = true;
    }

    public static void queueUseClick() {
        if (PackHideState.isHardLocked()) return;
        useQueued = true;
    }

    public static void queueFastExpUseClick() {
        queuePacedUseClick(PacedUseOwner.FAST_EXP);
    }

    public static void queueFastBlockUseClick() {
        queuePacedUseClick(PacedUseOwner.FAST_BLOCK);
    }

    public static boolean beginFastExpUseClick() {
        return beginPacedUseClick(PacedUseOwner.FAST_EXP);
    }

    public static boolean beginFastBlockUseClick() {
        return beginPacedUseClick(PacedUseOwner.FAST_BLOCK);
    }

    public static boolean isFastExpUseInProgress() {
        return pacedUseInProgress == PacedUseOwner.FAST_EXP;
    }

    public static void cancelFastExpUseClick() {
        cancelPacedUseClick(PacedUseOwner.FAST_EXP);
    }

    public static void cancelFastBlockUseClick() {
        cancelPacedUseClick(PacedUseOwner.FAST_BLOCK);
    }

    private static void queuePacedUseClick(PacedUseOwner owner) {
        if (PackHideState.isHardLocked() || owner == null || owner == PacedUseOwner.NONE) return;
        pacedUseQueued = owner;
    }

    private static boolean beginPacedUseClick(PacedUseOwner owner) {
        if (pacedUseActive != owner) return false;
        pacedUseActive = PacedUseOwner.NONE;
        pacedUseInProgress = owner;
        return true;
    }

    private static void cancelPacedUseClick(PacedUseOwner owner) {
        if (pacedUseQueued == owner) pacedUseQueued = PacedUseOwner.NONE;
        if (pacedUseActive == owner && MC != null && MC.options != null) {
            simulate(MC.options.keyUse, false);
            usePressed = false;
        }
        if (pacedUseActive == owner) pacedUseActive = PacedUseOwner.NONE;
        if (pacedUseInProgress == owner) pacedUseInProgress = PacedUseOwner.NONE;
        if (MC != null && MC.options != null && MC.options.keyUse != null) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$resetPressedState();
        }
    }

    public static void queueHotbarSlot(int slot) {
        if (PackHideState.isHardLocked()) return;
        hotbarSlotQueued = Math.max(0, Math.min(8, slot));
    }

    public static void beforeHandleKeybinds() {
        if (!canProcessInput()) {
            clear();
            return;
        }
        if (attackQueued) {
            simulate(MC.options.keyAttack, true);
            attackPressed = true;
        }
        if (useQueued || pacedUseQueued != PacedUseOwner.NONE) {
            pacedUseActive = pacedUseQueued;
            if (pacedUseActive != PacedUseOwner.NONE) {

                while (MC.options.keyUse.consumeClick()) {

                }
            }
            simulate(MC.options.keyUse, true);
            usePressed = true;
        }
        if (hotbarSlotQueued >= 0 && MC.options.keyHotbarSlots != null && hotbarSlotQueued < MC.options.keyHotbarSlots.length) {
            hotbarPressed = MC.options.keyHotbarSlots[hotbarSlotQueued];
            simulate(hotbarPressed, true);
        }
        attackQueued = false;
        useQueued = false;
        pacedUseQueued = PacedUseOwner.NONE;
        hotbarSlotQueued = -1;
    }

    public static void onClientTickStart() {
        if (!canProcessInput()) clear();
    }

    public static void afterHandleKeybinds() {
        if (MC == null || MC.options == null) {
            attackPressed = false;
            usePressed = false;
            pacedUseActive = PacedUseOwner.NONE;
            pacedUseInProgress = PacedUseOwner.NONE;
            return;
        }
        if (attackPressed) {
            simulate(MC.options.keyAttack, false);
            attackPressed = false;
        }
        if (usePressed) {
            simulate(MC.options.keyUse, false);
            usePressed = false;
        }
        pacedUseActive = PacedUseOwner.NONE;
        pacedUseInProgress = PacedUseOwner.NONE;
        if (hotbarPressed != null) {
            simulate(hotbarPressed, false);
            hotbarPressed = null;
        }
    }

    public static void clear() {
        attackQueued = false;
        useQueued = false;
        pacedUseQueued = PacedUseOwner.NONE;
        hotbarSlotQueued = -1;
        afterHandleKeybinds();

        drainStalePhysicalClicks();
    }

    private static void drainStalePhysicalClicks() {
        if (MC == null || MC.options == null) return;
        drainClick(MC.options.keyUse);
        drainClick(MC.options.keyAttack);
    }

    private static void drainClick(KeyMapping mapping) {
        if (mapping == null) return;
        while (mapping.consumeClick()) {

        }
        AutismKeyMappingBridge.of(mapping).autism$resetPressedState();
    }

    private static boolean canProcessInput() {
        return MC != null
            && MC.player != null
            && MC.level != null
            && MC.options != null
            && MC.getWindow() != null
            && MC.gui.screen() == null
            && MC.gui.overlay() == null
            && !PackHideState.isActive();
    }

    private static void simulate(KeyMapping mapping, boolean pressed) {
        if (mapping != null) AutismKeyMappingBridge.of(mapping).autism$simulatePress(pressed);
    }
}
