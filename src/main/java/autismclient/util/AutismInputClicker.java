package autismclient.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import autismclient.modules.PackHideState;

public final class AutismInputClicker {
    private enum PacedUseOwner {
        NONE,
        FAST_BLOCK,
        FAST_EXP,
        SCAFFOLD
    }

    private static final Minecraft MC = Minecraft.getInstance();
    private static boolean attackQueued;
    private static boolean useQueued;
    private static PacedUseOwner pacedUseQueued = PacedUseOwner.NONE;
    private static long scaffoldGenerationQueued;
    private static int hotbarSlotQueued = -1;
    private static boolean attackPressed;
    private static boolean usePressed;
    private static PacedUseOwner pacedUseActive = PacedUseOwner.NONE;
    private static PacedUseOwner pacedUseInProgress = PacedUseOwner.NONE;
    private static long scaffoldGenerationActive;
    private static long scaffoldGenerationInProgress;
    private static KeyMapping hotbarPressed;
    private static boolean attackHeld;
    private static boolean useHeld;

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

    public static void queueScaffoldUseClick(long generation) {
        if (generation <= 0L || PackHideState.isHardLocked()) return;
        scaffoldGenerationQueued = generation;
        queuePacedUseClick(PacedUseOwner.SCAFFOLD);
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

    public static boolean beginScaffoldUseClick(long generation) {
        if (generation <= 0L || scaffoldGenerationActive != generation) return false;
        if (!beginPacedUseClick(PacedUseOwner.SCAFFOLD)) return false;
        scaffoldGenerationInProgress = generation;
        scaffoldGenerationActive = 0L;
        return true;
    }

    public static boolean isScaffoldUseInProgress() {
        return pacedUseInProgress == PacedUseOwner.SCAFFOLD;
    }

    public static long scaffoldUseGenerationInProgress() {
        return isScaffoldUseInProgress() ? scaffoldGenerationInProgress : 0L;
    }

    public static void cancelScaffoldUseClick() {
        if (pacedUseQueued != PacedUseOwner.SCAFFOLD
            && pacedUseActive != PacedUseOwner.SCAFFOLD
            && pacedUseInProgress != PacedUseOwner.SCAFFOLD
            && scaffoldGenerationQueued == 0L && scaffoldGenerationActive == 0L
            && scaffoldGenerationInProgress == 0L) return;
        cancelPacedUseClick(PacedUseOwner.SCAFFOLD);
        scaffoldGenerationQueued = 0L;
        scaffoldGenerationActive = 0L;
        scaffoldGenerationInProgress = 0L;
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
        if (owner == PacedUseOwner.SCAFFOLD) {
            scaffoldGenerationQueued = 0L;
            scaffoldGenerationActive = 0L;
            scaffoldGenerationInProgress = 0L;
        }
        if (MC != null && MC.options != null && MC.options.keyUse != null) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$resetPressedState();
        }
    }

    public static void setAttackHeld(boolean held) {
        attackHeld = applyHold(MC == null || MC.options == null ? null : MC.options.keyAttack, attackHeld, held);
    }

    public static void setUseHeld(boolean held) {
        useHeld = applyHold(MC == null || MC.options == null ? null : MC.options.keyUse, useHeld, held);
    }

    private static boolean applyHold(KeyMapping mapping, boolean current, boolean held) {
        if (mapping == null) return false;
        if (held && !PackHideState.isHardLocked() && canProcessInput()) {
            if (!mapping.isDown()) simulate(mapping, true);
            return true;
        }
        if (current) {
            simulate(mapping, false);
            AutismKeyMappingBridge.of(mapping).autism$resetPressedState();
        }
        return false;
    }

    private static void releaseHolds() {
        setAttackHeld(false);
        setUseHeld(false);
    }

    public static void queueHotbarSlot(int slot) {
        if (PackHideState.isHardLocked()) return;
        hotbarSlotQueued = Math.max(0, Math.min(8, slot));
    }

    public static void beforeHandleKeybinds() {
        if (!canProcessInput()) {
            standDown();
            return;
        }

        boolean combatOwnsUse = AutismCombatClicker.ownsKeyUseThisTick();
        boolean combatOwnsAttack = AutismCombatClicker.ownsKeyAttackThisTick();
        if (attackQueued && !combatOwnsAttack) {
            simulate(MC.options.keyAttack, true);
            attackPressed = true;
        }
        if (!combatOwnsAttack) attackQueued = false;
        if (!combatOwnsUse && (useQueued || pacedUseQueued != PacedUseOwner.NONE)) {

            boolean plainWinsTick = useQueued && pacedUseQueued != PacedUseOwner.NONE;
            pacedUseActive = plainWinsTick ? PacedUseOwner.NONE : pacedUseQueued;
            scaffoldGenerationActive = pacedUseActive == PacedUseOwner.SCAFFOLD
                ? scaffoldGenerationQueued : 0L;
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
        if (!combatOwnsUse) {
            useQueued = false;

            if (pacedUseActive != PacedUseOwner.NONE || pacedUseQueued == PacedUseOwner.NONE) {
                pacedUseQueued = PacedUseOwner.NONE;
                scaffoldGenerationQueued = 0L;
            }
        }
        hotbarSlotQueued = -1;
    }

    public static void onClientTickStart() {
        if (!canProcessInput()) standDown();
    }

    public static void afterHandleKeybinds() {
        if (MC == null || MC.options == null) {
            attackPressed = false;
            usePressed = false;
            pacedUseActive = PacedUseOwner.NONE;
            pacedUseInProgress = PacedUseOwner.NONE;
            scaffoldGenerationActive = 0L;
            scaffoldGenerationInProgress = 0L;
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
        scaffoldGenerationActive = 0L;
        scaffoldGenerationInProgress = 0L;
        if (hotbarPressed != null) {
            simulate(hotbarPressed, false);
            hotbarPressed = null;
        }
    }

    private static void standDown() {
        if (physicalClicksAreStale()) clear();
        else releaseOwnedInput();
    }

    private static boolean physicalClicksAreStale() {
        return MC == null || MC.player == null || MC.level == null || MC.options == null
            || MC.getWindow() == null || MC.gui.screen() != null || MC.gui.overlay() != null;
    }

    public static void releaseOwnedInput() {
        attackQueued = false;
        useQueued = false;
        pacedUseQueued = PacedUseOwner.NONE;
        scaffoldGenerationQueued = 0L;
        scaffoldGenerationActive = 0L;
        scaffoldGenerationInProgress = 0L;
        hotbarSlotQueued = -1;
        afterHandleKeybinds();
        releaseHolds();
    }

    public static void clear() {
        attackQueued = false;
        useQueued = false;
        pacedUseQueued = PacedUseOwner.NONE;
        scaffoldGenerationQueued = 0L;
        scaffoldGenerationActive = 0L;
        scaffoldGenerationInProgress = 0L;
        hotbarSlotQueued = -1;
        afterHandleKeybinds();
        releaseHolds();

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
