package autismclient.util;

import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class AutismCombatClicker {
    private static final Minecraft MC = Minecraft.getInstance();

    private static BlockHitResult pendingUseHit;
    private static InteractionHand pendingUseHand;
    private static EntityHitResult pendingAttackHit;
    private static BlockHitResult pendingMissHit;

    private static BlockHitResult pendingHoldUseHit;
    private static InteractionHand pendingHoldUseHand;
    private static BlockHitResult pendingHoldAttackHit;

    private static boolean heldUse;
    private static boolean heldAttack;

    private static boolean useInFlight;
    private static boolean attackInFlight;
    private static boolean pressedUse;
    private static boolean pressedAttack;

    private static boolean suppressedUse;
    private static boolean suppressedAttack;
    private static Float savedYaw;
    private static float savedPitch;

    private AutismCombatClicker() {
    }

    public static boolean queueUse(BlockHitResult hit, InteractionHand hand) {
        if (hit == null || hand == null || PackHideState.isHardLocked() || pendingUseHit != null
            || pendingAttackHit != null || pendingMissHit != null || holding() || holdRequested()) return false;
        pendingUseHit = hit;
        pendingUseHand = hand;
        return true;
    }

    public static boolean queueAttack(EntityHitResult hit) {
        if (hit == null || PackHideState.isHardLocked() || pendingUseHit != null
            || pendingAttackHit != null || pendingMissHit != null || holding() || holdRequested()) {
            return false;
        }
        pendingAttackHit = hit;
        return true;
    }

    public static boolean queueAttackMiss(BlockHitResult miss) {
        if (miss == null || PackHideState.isHardLocked() || pendingUseHit != null
            || pendingAttackHit != null || pendingMissHit != null || holding() || holdRequested()) {
            return false;
        }
        pendingMissHit = miss;
        return true;
    }

    public static boolean holdAttack(BlockHitResult ray) {
        if (ray == null || MC == null || MC.player == null || MC.options == null
            || PackHideState.isHardLocked() || pendingUseHit != null || pendingAttackHit != null
            || pendingMissHit != null || MC.gui.screen() != null || MC.gui.overlay() != null) {
            return false;
        }

        if (heldUse || pendingHoldUseHit != null) return false;

        pendingHoldAttackHit = ray;
        return true;
    }

    public static boolean holdUse(BlockHitResult ray, InteractionHand hand) {
        if (ray == null || hand == null || MC == null || MC.player == null || MC.options == null
            || PackHideState.isHardLocked() || pendingUseHit != null || pendingAttackHit != null
            || pendingMissHit != null || MC.gui.screen() != null || MC.gui.overlay() != null) {
            return false;
        }

        if (heldAttack || pendingHoldAttackHit != null) return false;
        pendingHoldUseHit = ray;

        pendingHoldUseHand = hand;
        return true;
    }

    public static void releaseHold() {
        pendingHoldUseHit = null;
        pendingHoldUseHand = null;
        pendingHoldAttackHit = null;
        if (MC == null || MC.options == null) {
            heldUse = false;
            heldAttack = false;
            return;
        }
        if (heldAttack) {
            AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(false);

            AutismKeyMappingBridge.of(MC.options.keyAttack).autism$resetPressedState();
            heldAttack = false;
        }
        if (heldUse) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(false);
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$resetPressedState();
            heldUse = false;
        }

        if (savedYaw != null && MC.player != null) {
            MC.player.setYRot(savedYaw);
            MC.player.setXRot(savedPitch);
            savedYaw = null;
        }
    }

    public static boolean holding() {
        return heldUse || heldAttack;
    }

    private static boolean holdRequested() {
        return pendingHoldUseHit != null || pendingHoldAttackHit != null;
    }

    public static boolean mainHandWouldPreempt() {
        ItemStack main = MC == null || MC.player == null ? ItemStack.EMPTY : MC.player.getMainHandItem();
        if (main.isEmpty()) return false;

        if (main.getItem() instanceof BlockItem) return true;
        if (main.getUseAnimation() != net.minecraft.world.item.ItemUseAnimation.NONE) return true;

        if (main.has(net.minecraft.core.component.DataComponents.EQUIPPABLE)) return true;

        if (main.is(Items.EXPERIENCE_BOTTLE) || main.is(Items.ENDER_PEARL)
            || main.is(Items.SNOWBALL) || main.is(Items.EGG) || main.is(Items.FIREWORK_ROCKET)
            || main.is(Items.FISHING_ROD) || main.getItem() instanceof net.minecraft.world.item.BucketItem
            || main.is(Items.WIND_CHARGE) || main.is(Items.FIRE_CHARGE)
            || main.is(Items.ENDER_EYE) || main.is(Items.SPLASH_POTION) || main.is(Items.LINGERING_POTION)
            || main.is(Items.FLINT_AND_STEEL) || main.is(Items.BONE_MEAL) || main.is(Items.GLASS_BOTTLE)
            || main.is(Items.CARROT_ON_A_STICK)) return true;

        return main.getItem() instanceof net.minecraft.world.item.SpawnEggItem
            || main.getItem() instanceof net.minecraft.world.item.BoatItem
            || main.getItem() instanceof net.minecraft.world.item.MinecartItem
            || main.getItem() instanceof net.minecraft.world.item.ItemFrameItem
            || main.getItem() instanceof net.minecraft.world.item.ShovelItem
            || main.getItem() instanceof net.minecraft.world.item.AxeItem
            || main.getItem() instanceof net.minecraft.world.item.HoeItem
            || main.getItem() instanceof net.minecraft.world.item.ShearsItem;
    }

    public static void cancel() {
        pendingUseHit = null;
        pendingUseHand = null;
        pendingAttackHit = null;
        pendingMissHit = null;
        useInFlight = false;
        attackInFlight = false;

        releaseHold();
        if (MC != null && MC.options != null) {
            if (pressedUse) {
                AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(false);

                AutismKeyMappingBridge.of(MC.options.keyUse).autism$resetPressedState();
                pressedUse = false;
            }
            if (pressedAttack) {
                AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(false);
                AutismKeyMappingBridge.of(MC.options.keyAttack).autism$resetPressedState();
                pressedAttack = false;
            }
        }
        if (savedYaw != null && MC != null && MC.player != null) {
            MC.player.setYRot(savedYaw);
            MC.player.setXRot(savedPitch);
            savedYaw = null;
        }
    }

    public static boolean useInFlight() {
        return useInFlight;
    }

    public static boolean beginUse() {
        if (!useInFlight) return false;
        useInFlight = false;
        return true;
    }

    public static boolean attackInFlight() {
        return attackInFlight;
    }

    public static boolean beginAttack() {
        if (!attackInFlight) return false;
        attackInFlight = false;
        return true;
    }

    public static boolean ownsKeyUseThisTick() {
        return pendingUseHit != null || useInFlight || pressedUse || heldUse || pendingHoldUseHit != null;
    }

    public static boolean ownsKeyAttackThisTick() {
        return pendingAttackHit != null || pendingMissHit != null || attackInFlight || pressedAttack
            || heldAttack || pendingHoldAttackHit != null;
    }

    public static void beforeHandleKeybinds() {
        if (MC == null || MC.player == null || MC.options == null || PackHideState.isHardLocked()) {
            cancel();
            return;
        }
        BlockHitResult useHit = pendingUseHit;
        EntityHitResult attackHit = pendingAttackHit;
        BlockHitResult missHit = pendingMissHit;
        BlockHitResult holdAttackRay = pendingHoldAttackHit;
        BlockHitResult holdUseRay = pendingHoldUseHit;
        if (useHit == null && attackHit == null && missHit == null && holdAttackRay == null && holdUseRay == null) {

            if (heldAttack || heldUse) releaseHold();
            return;
        }
        if (MC.gui.screen() != null || MC.gui.overlay() != null) {

            cancel();
            return;
        }

        pendingUseHit = null;
        pendingUseHand = null;
        pendingAttackHit = null;
        pendingMissHit = null;
        pendingHoldAttackHit = null;
        pendingHoldUseHit = null;
        pendingHoldUseHand = null;

        savedYaw = MC.player.getYRot();
        savedPitch = MC.player.getXRot();
        AutismServerRotationView.WireSnapshot wire = AutismServerRotationView.snapshot();
        if (wire.initialized()) {
            MC.player.setYRot(wire.currentYaw());
            MC.player.setXRot(wire.currentPitch());
        }

        if (attackHit != null) {
            MC.hitResult = attackHit;
            drainAllKeys();
            suppressOppositeKey(MC.options.keyUse, true);
            AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(true);
            pressedAttack = true;
            attackInFlight = true;
            return;
        }
        if (missHit != null) {
            MC.hitResult = missHit;
            drainAllKeys();
            suppressOppositeKey(MC.options.keyUse, true);
            AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(true);
            pressedAttack = true;
            attackInFlight = true;
            return;
        }
        if (useHit != null) {
            MC.hitResult = useHit;
            drainAllKeys();
            suppressOppositeKey(MC.options.keyAttack, false);
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(true);
            pressedUse = true;
            useInFlight = true;
            return;
        }
        if (holdAttackRay != null) {
            MC.hitResult = holdAttackRay;
            drainAllKeys();
            suppressOppositeKey(MC.options.keyUse, true);
            if (!heldAttack) {

                AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(false);
                AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(true);
                heldAttack = true;
                attackInFlight = true;
            }
            return;
        }
        MC.hitResult = holdUseRay;
        drainAllKeys();
        suppressOppositeKey(MC.options.keyAttack, false);
        if (!heldUse) {

            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(false);
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(true);
            heldUse = true;
        }

        useInFlight = true;
    }

    private static void suppressOppositeKey(net.minecraft.client.KeyMapping key, boolean use) {
        AutismKeyMappingBridge.of(key).autism$simulatePress(false);
        if (use) suppressedUse = true;
        else suppressedAttack = true;
    }

    private static void drainAllKeys() {
        while (MC.options.keyUse.consumeClick()) {

        }
        while (MC.options.keyAttack.consumeClick()) {

        }
        while (MC.options.keyPickItem.consumeClick()) {

        }
    }

    public static void afterHandleKeybinds() {
        if (MC == null || MC.options == null) {
            pressedUse = false;
            pressedAttack = false;
            suppressedUse = false;
            suppressedAttack = false;
            heldUse = false;
            heldAttack = false;
            useInFlight = false;
            attackInFlight = false;
            savedYaw = null;
            return;
        }
        if (pressedUse) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(false);

            AutismKeyMappingBridge.of(MC.options.keyUse).autism$resetPressedState();
            pressedUse = false;
        }
        if (pressedAttack) {
            AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(false);
            AutismKeyMappingBridge.of(MC.options.keyAttack).autism$resetPressedState();
            pressedAttack = false;
        }

        if (suppressedUse) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$resetPressedState();
            suppressedUse = false;
        }
        if (suppressedAttack) {
            AutismKeyMappingBridge.of(MC.options.keyAttack).autism$resetPressedState();
            suppressedAttack = false;
        }
        useInFlight = false;
        attackInFlight = false;
        if (savedYaw != null && MC.player != null) {
            MC.player.setYRot(savedYaw);
            MC.player.setXRot(savedPitch);
            savedYaw = null;
        }
    }
}
